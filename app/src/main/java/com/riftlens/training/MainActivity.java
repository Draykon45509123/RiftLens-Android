package com.riftlens.training;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.*;
import android.text.InputType;
import java.util.Arrays;
import java.util.Locale;

public class MainActivity extends Activity {
 private TextureView preview;
 private CameraDevice camera;
 private CameraCaptureSession session;
 private TextView status, result;
 private EditText base, haste;
 private boolean opening;
 private static final int PERMISSION=440;
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
  root.setPadding(18,24,18,16);
  ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
  TextView heading=new TextView(this);heading.setText("RiftLens · Análisis de repeticiones");heading.setTextSize(22);root.addView(heading);
  status=new TextView(this);status.setText("Apunta la cámara a una repetición pausada o en reproducción.");root.addView(status);
  preview=new TextureView(this); root.addView(preview,new LinearLayout.LayoutParams(-1,620));
  preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
   public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h){openCamera();}
   public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h){}
   public boolean onSurfaceTextureDestroyed(SurfaceTexture s){closeCamera();return true;}
   public void onSurfaceTextureUpdated(SurfaceTexture s){}
  });
  TextView note=new TextView(this);note.setText("Calculadora manual para estudiar repeticiones. No detecta habilidades automáticamente ni sigue enemigos en partidas activas.");root.addView(note);
  base=new EditText(this);base.setHint("Enfriamiento base (segundos)");base.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);root.addView(base);
  haste=new EditText(this);haste.setHint("Aceleración de habilidad confirmada");haste.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);root.addView(haste);
  Button calculate=new Button(this);calculate.setText("Calcular enfriamiento teórico");root.addView(calculate);
  result=new TextView(this);result.setTextSize(18);root.addView(result);
  calculate.setOnClickListener(v->calculate());
 }
 private void calculate(){
  try{
   double b=Double.parseDouble(base.getText().toString().trim());
   double a=Double.parseDouble(haste.getText().toString().trim());
   if(!Double.isFinite(b)||!Double.isFinite(a)||b<0||a<0||a>10000)throw new NumberFormatException();
   result.setText(String.format(Locale.US,"Enfriamiento estimado: %.2f s",b*100/(100+a)));
  }catch(NumberFormatException e){result.setText("Introduce valores numéricos válidos (>= 0).");}
 }
 private void openCamera(){
  if(camera!=null||opening||preview==null||!preview.isAvailable())return;
  if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
   requestPermissions(new String[]{Manifest.permission.CAMERA},PERMISSION);return;
  }
  try{
   CameraManager manager=(CameraManager)getSystemService(CAMERA_SERVICE);
   String selected=null;
   for(String id:manager.getCameraIdList()){
    CameraCharacteristics chars=manager.getCameraCharacteristics(id);
    Integer facing=chars.get(CameraCharacteristics.LENS_FACING);
    if(facing!=null&&facing==CameraCharacteristics.LENS_FACING_BACK){selected=id;break;}
   }
   if(selected==null){status.setText("No se encontró cámara posterior.");return;}
   opening=true;
   manager.openCamera(selected,new CameraDevice.StateCallback(){
    @Override public void onOpened(CameraDevice c){opening=false;camera=c;configure();}
    @Override public void onDisconnected(CameraDevice c){opening=false;c.close();camera=null;}
    @Override public void onError(CameraDevice c,int e){opening=false;c.close();camera=null;runOnUiThread(()->status.setText("Error de cámara: "+e));}
   },null);
  }catch(Exception e){opening=false;status.setText("No se pudo abrir la cámara: "+e.getMessage());}
 }
 private void configure(){
  try{
   if(camera==null||!preview.isAvailable())return;
   SurfaceTexture texture=preview.getSurfaceTexture();
   texture.setDefaultBufferSize(1280,720);
   Surface surface=new Surface(texture);
   CaptureRequest.Builder request=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
   request.addTarget(surface);
   camera.createCaptureSession(Arrays.asList(surface),new CameraCaptureSession.StateCallback(){
    @Override public void onConfigured(CameraCaptureSession s){
     session=s;
     try{s.setRepeatingRequest(request.build(),null,null);runOnUiThread(()->status.setText("Cámara activa · modo repetición"));}catch(Exception e){runOnUiThread(()->status.setText("Falló vista previa"));}
    }
    @Override public void onConfigureFailed(CameraCaptureSession s){runOnUiThread(()->status.setText("No fue posible configurar la cámara."));}
   },null);
  }catch(Exception e){runOnUiThread(()->status.setText("Error configurando cámara: "+e.getMessage()));}
 }
 private void closeCamera(){if(session!=null){session.close();session=null;}if(camera!=null){camera.close();camera=null;}opening=false;}
 @Override protected void onResume(){super.onResume();if(preview!=null&&preview.isAvailable())openCamera();}
 @Override protected void onPause(){closeCamera();super.onPause();}
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
  super.onRequestPermissionsResult(request,permissions,results);
  if(request==PERMISSION&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)openCamera();
  else status.setText("Se necesita permiso de cámara para mostrar la vista.");
 }
}
