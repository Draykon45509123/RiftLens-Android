package com.riftlens.training;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.TextureView;
import android.widget.*;
import android.text.InputType;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
/**
 * RiftLens: experimental ON-DEVICE visual learning for replay review only.
 * No network requests, no screen capture, no game process interaction.
 * Classifies small image crops against manually labeled examples.
 */
public class MainActivity extends Activity {
 private TextureView preview;
 private CameraDevice camera;
 private CameraCaptureSession session;
 private TextView status, info, countdowns;
 private EditText cooldownBase, haste, champion;
 private Spinner skill;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private boolean scanning=false, opening=false, inFlight=false;
 private final String[] keys={"Q","W","E","R"};
 private final Map<String,ArrayList<double[]>> examples=new HashMap<>();
 private final Map<String,Double> finishAt=new HashMap<>();
 private final Map<String,Double> baseTimes=new HashMap<>();
 private String lastMatch=""; private int consecutive=0;
 private long lastTrigger=0L;
 private final Runnable scanner=new Runnable(){public void run(){
  if(scanning){inspect();handler.postDelayed(this,350);}
 }};
 private final Runnable tick=new Runnable(){public void run(){renderCooldowns();handler.postDelayed(this,250);}};
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  for(String k:keys){examples.put(k,new ArrayList<>());baseTimes.put(k,12.0);}
  loadExamples();
  ScrollView scroll=new ScrollView(this);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(20,24,20,20);
  scroll.addView(root);setContentView(scroll);
  addLabel(root,"RiftLens 0.7 · IA visual local",23);
  addLabel(root,"MODO REPETICIONES: orienta la cámara hacia una repetición, pausa en una animación y guarda ejemplos etiquetados. No usar para medir enemigos en partidas activas.",13);
  champion=new EditText(this);champion.setSingleLine();champion.setHint("Campeón observado (ejemplo: Darius)");root.addView(champion);
  preview=new TextureView(this);root.addView(preview,new LinearLayout.LayoutParams(-1,600));
  preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
   public void onSurfaceTextureAvailable(SurfaceTexture t,int w,int h){openCamera();}
   public void onSurfaceTextureSizeChanged(SurfaceTexture t,int w,int h){}
   public boolean onSurfaceTextureDestroyed(SurfaceTexture t){closeCamera();return true;}
   public void onSurfaceTextureUpdated(SurfaceTexture t){}
  });
  status=addLabel(root,"Preparando cámara...",14);
  addLabel(root,"Entrenar IA: selecciona habilidad, encuadra SOLO la zona relevante y guarda varias capturas del mismo lanzamiento.",14);
  skill=new Spinner(this);ArrayAdapter<String> adap=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,keys);
  adap.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);skill.setAdapter(adap);root.addView(skill);
  Button learn=new Button(this);learn.setText("Aprender imagen de habilidad");root.addView(learn);
  learn.setOnClickListener(v->learn());
  Button clear=new Button(this);clear.setText("Borrar ejemplos de esta habilidad");root.addView(clear);
  clear.setOnClickListener(v->{String k=keys[skill.getSelectedItemPosition()];examples.get(k).clear();saveExamples();status.setText("Ejemplos de "+k+" borrados.");});
  addLabel(root,"Enfriamiento base de la habilidad seleccionada (nivel/rango), en segundos:",13);
  cooldownBase=new EditText(this);cooldownBase.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);cooldownBase.setText("12");root.addView(cooldownBase);
  haste=new EditText(this);haste.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);haste.setHint("Aceleración de habilidad confirmada");haste.setText("0");root.addView(haste);
  Button set=new Button(this);set.setText("Guardar enfriamiento base");root.addView(set);
  set.setOnClickListener(v->{try{double d=Double.parseDouble(cooldownBase.getText().toString());if(d<0||d>300)throw new Exception();baseTimes.put(keys[skill.getSelectedItemPosition()],d);status.setText("Base guardada.");}catch(Exception e){status.setText("Base no válida.");}});
  Button start=new Button(this);start.setText("Analizar repetición (iniciar / detener)");root.addView(start);
  start.setOnClickListener(v->{scanning=!scanning;lastMatch="";consecutive=0;status.setText(scanning?"Analizando cambios en el encuadre...":"Análisis detenido.");if(scanning)handler.post(scanner);});
  info=addLabel(root,"Motor: coincidencia visual mediante ejemplos locales (no modelo preentrenado).",13);
  countdowns=addLabel(root,"Sin eventos",20);
  handler.post(tick);
 }
 private TextView addLabel(LinearLayout root,String t,int size){TextView x=new TextView(this);x.setText(t);x.setTextSize(size);x.setPadding(0,8,0,8);root.addView(x);return x;}
 private double[] feature(Bitmap full){
  int width=full.getWidth(),height=full.getHeight();
  // Center crop: camera must aim cropped region at the replay area.
  int side=Math.min(width,height)*2/3, left=(width-side)/2,top=(height-side)/2;
  Bitmap part=Bitmap.createBitmap(full,left,top,side,side);
  Bitmap small=Bitmap.createScaledBitmap(part,16,16,true);
  double[] a=new double[256];double mean=0;
  for(int y=0;y<16;y++)for(int x=0;x<16;x++){int c=small.getPixel(x,y);double val=(Color.red(c)*.299+Color.green(c)*.587+Color.blue(c)*.114)/255.0;a[y*16+x]=val;mean+=val;}
  mean/=256;double norm=0;for(int i=0;i<256;i++){a[i]-=mean;norm+=a[i]*a[i];}
  norm=Math.sqrt(norm);if(norm>1e-6)for(int i=0;i<256;i++)a[i]/=norm;
  small.recycle();part.recycle();return a;
 }
 private double[] snapshot(){if(preview==null||!preview.isAvailable())return null;Bitmap b=preview.getBitmap(320,320);if(b==null)return null;try{return feature(b);}finally{b.recycle();}}
 private void learn(){
  double[] a=snapshot();if(a==null){status.setText("No hay imagen de cámara.");return;}
  String k=keys[skill.getSelectedItemPosition()];
  examples.get(k).add(a);saveExamples();
  status.setText("Ejemplo guardado para "+k+" ("+examples.get(k).size()+").");
 }
 private double distance(double[] a,double[] b){double score=0;for(int i=0;i<a.length;i++){double d=a[i]-b[i];score+=d*d;}return Math.sqrt(score);}
 private void inspect(){
  if(inFlight)return;inFlight=true;
  try{
   double[] f=snapshot();if(f==null)return;
   double nearest=Double.MAX_VALUE;String choice="";
   for(String k:keys)for(double[] ex:examples.get(k)){double dist=distance(f,ex);if(dist<nearest){nearest=dist;choice=k;}}
   // Conservative threshold; requires two confirmations and a local debounce.
   if(choice.isEmpty()||nearest>0.17){lastMatch="";consecutive=0;info.setText("Sin coincidencia segura. Muestras: "+count());return;}
   if(choice.equals(lastMatch))consecutive++;else{lastMatch=choice;consecutive=1;}
   info.setText("Parecido a "+choice+" · distancia "+String.format(Locale.US,"%.3f",nearest)+" · confirmaciones "+consecutive);
   if(consecutive>=2&&System.currentTimeMillis()-lastTrigger>3500){
    double ah=Double.parseDouble(haste.getText().toString());
    if(ah>=0&&ah<=10000){
     double duration=baseTimes.get(choice)*100.0/(100.0+ah);
     finishAt.put(choice,System.currentTimeMillis()/1000.0+duration);
     status.setText("POSIBLE "+choice+" — evento de repetición (no confirmado).");
     lastTrigger=System.currentTimeMillis();
    }
    consecutive=0;lastMatch="";
   }
  }catch(Exception e){status.setText("No se pudo analizar: "+e.getMessage());}
  finally{inFlight=false;}
 }
 private int count(){int n=0;for(String k:keys)n+=examples.get(k).size();return n;}
 private void renderCooldowns(){
  if(countdowns==null)return;StringBuilder b=new StringBuilder();
  double now=System.currentTimeMillis()/1000.0;
  for(String k:keys){Double end=finishAt.get(k);b.append(k).append(": ");b.append(end==null?"sin evento":(end<=now?"terminado":String.format(Locale.US,"%.1f s estimados",end-now))).append("\n");}
  countdowns.setText(b.toString());
 }
 private void loadExamples(){try{
  JSONObject db=new JSONObject(getPreferences(0).getString("training","{}"));
  for(String k:keys){JSONArray data=db.optJSONArray(k);if(data==null)continue;
   for(int n=0;n<data.length();n++){JSONArray src=data.getJSONArray(n);if(src.length()!=256)continue;double[] arr=new double[256];for(int i=0;i<256;i++)arr[i]=src.getDouble(i);examples.get(k).add(arr);}
  }
 }catch(Exception ignored){}}
 private void saveExamples(){try{
  JSONObject db=new JSONObject();for(String k:keys){JSONArray data=new JSONArray();
   for(double[] a:examples.get(k)){JSONArray row=new JSONArray();for(double d:a)row.put(d);data.put(row);}
   db.put(k,data);}
  getPreferences(0).edit().putString("training",db.toString()).apply();
 }catch(Exception ignored){}}
 private void openCamera(){
  if(opening||camera!=null||preview==null||!preview.isAvailable())return;
  if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},201);return;}
  try{
   CameraManager m=(CameraManager)getSystemService(CAMERA_SERVICE);String id=null;
   for(String s:m.getCameraIdList())if(Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK).equals(m.getCameraCharacteristics(s).get(CameraCharacteristics.LENS_FACING))) {id=s;break;}
   if(id==null){status.setText("No hay cámara posterior.");return;}opening=true;
   m.openCamera(id,new CameraDevice.StateCallback(){
    @Override public void onOpened(CameraDevice c){opening=false;camera=c;configure();}
    @Override public void onDisconnected(CameraDevice c){opening=false;c.close();camera=null;}
    @Override public void onError(CameraDevice c,int err){opening=false;c.close();camera=null;}
   },null);
  }catch(Exception e){opening=false;status.setText("Error abriendo cámara.");}
 }
 private void configure(){try{
  if(camera==null||!preview.isAvailable())return;
  SurfaceTexture t=preview.getSurfaceTexture();t.setDefaultBufferSize(1280,720);Surface s=new Surface(t);
  CaptureRequest.Builder req=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);req.addTarget(s);
  camera.createCaptureSession(Arrays.asList(s),new CameraCaptureSession.StateCallback(){
   @Override public void onConfigured(CameraCaptureSession sess){session=sess;try{sess.setRepeatingRequest(req.build(),null,null);runOnUiThread(()->status.setText("Cámara disponible."));}catch(Exception ignored){}}
   @Override public void onConfigureFailed(CameraCaptureSession sess){runOnUiThread(()->status.setText("Error en vista previa."));}
  },null);
 }catch(Exception e){status.setText("Error de configuración.");}}
 private void closeCamera(){if(session!=null){session.close();session=null;}if(camera!=null){camera.close();camera=null;}opening=false;}
 @Override protected void onResume(){super.onResume();if(preview!=null&&preview.isAvailable())openCamera();}
 @Override protected void onPause(){scanning=false;closeCamera();super.onPause();}
 @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
 @Override public void onRequestPermissionsResult(int req,String[] p,int[] results){super.onRequestPermissionsResult(req,p,results);if(req==201&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)openCamera();}
}
