# ProVoiceChanger

Base Android nativa para un cambiador de voz en tiempo real en Huawei Mate 10 Lite
con Android 8/API 26.

## Estado actual

- App Android con paquete unico `com.provoicechanger`.
- Pantalla principal estilo demonio, basada en la referencia `scremDemon.jpg`.
- Boton central de activacion.
- Areas laterales para seleccionar entrada y salida de audio.
- Sliders en tiempo real: `CHUNK`, `PITCH` y `DISTORSION`.
- Servicio en primer plano para procesar audio.
- Procesamiento local: entrada de microfono, efecto y salida por altavoz o auriculares.

## Limite importante

Android no permite que una app normal sustituya el microfono del sistema para otras apps como WhatsApp, Discord o juegos. Como este dispositivo tiene Magisk/root, podemos estudiar una fase root, pero eso ya no seria una app Android normal: habria que investigar audio policy, AudioFlinger, modulos Magisk o hooks especificos del sistema del Huawei.

La fase actual deja una base estable: microfono a efecto y salida local.

## Compilar

1. Abre esta carpeta con Android Studio:
   `C:\Users\JAYLIZ\Documents\Apps\ProVoiceChanger`
2. Espera a que sincronice Gradle.
3. Ejecuta `Build > Build Bundle(s) / APK(s) > Build APK(s)`.

El proyecto usa Android Gradle Plugin `9.2.1`, JDK 17, `minSdk 26` y `targetSdk 26`.

## Ruta root propuesta

1. Confirmar version exacta: Android 8.0 u 8.1, EMUI y arquitectura.
2. Probar primero el modo local de baja latencia.
3. Explorar si el sistema expone rutas utiles en `/vendor/etc/audio_policy*`.
4. Si existe camino viable, crear un modulo Magisk separado; no mezclarlo con la app base.

<p align="center">
  <img src="https://github.com/user-attachments/assets/682c78c5-ea2e-4176-b8bc-aaee9e063e6f" width="30%" />
  <img src="https://github.com/user-attachments/assets/fbee727d-e802-479c-b251-d595c0e569a0" width="30%" />
  <img src="https://github.com/user-attachments/assets/f2b72652-43ee-4087-858a-20433138d0cb" width="30%" />
</p>



