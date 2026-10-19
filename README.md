# RiftLens 0.7 · laboratorio de visión para repeticiones

Prototipo Android de visión local sin conexión: cámara Camera2, entrenamiento manual de ejemplos Q/W/E/R, comparación de fotogramas, señales de posible evento y cronómetros estimados sobre **repeticiones**.

## Uso

1. Enfoca una repetición pausada; encuadra en el centro la zona de la habilidad.
2. Selecciona Q/W/E/R y registra varios ejemplos de un momento de lanzamiento; añade ejemplos variados.
3. Introduce el enfriamiento base de esa habilidad según su rango y la aceleración confirmada. Pulsa Guardar.
4. Reproduce la repetición y pulsa Analizar. La aplicación compara fotogramas con los ejemplos y muestra **posibles** eventos.

## Limitaciones importantes

- El algoritmo es de similitud de imágenes (256 píxeles normalizados), **NO una red neuronal ni un modelo previamente entrenado**. No interpreta la habilidad a partir de su significado y requiere ejemplos manuales.
- No identifica automáticamente campeón, objetos, aceleración, nivel de habilidad o parche.
- Puede dar numerosos falsos positivos y falsos negativos; los tiempos son aproximaciones.
- Ningún seguimiento de enemigos en partidas activas. No se integra con el proceso del juego.
- Las muestras se almacenan en preferencias internas del teléfono; no se suben a ningún servidor.
- Android 8.0+, permiso de cámara.

## Compilación
Abre GitHub Actions > Build RiftLens APK > último workflow correcto > Artifacts > RiftLens-APK-debug y extrae app-debug.apk.
