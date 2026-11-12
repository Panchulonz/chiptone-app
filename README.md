# Chiptone

Leitmotivs chiptune aleatorios para las notificaciones de WhatsApp (y otras apps). Cada mensaje suena distinto; mensajes seguidos arman una frase de 4 compases.

## Ediciones
- **owner** (`cl.conclusa.chiptone.owner`): todo desbloqueado, para el dueño. Se instala por APK.
- **play** (`cl.conclusa.chiptone`): versión para Google Play (cuenta Conclusa), gratis + Pro.

## Cómo obtener el APK
Cada push a `main` compila con GitHub Actions y publica un *Release* `build-N` con `chiptone-owner.apk`.
En el celular: abre el Release → descarga `chiptone-owner.apk` → instala (permite "instalar apps desconocidas").

## Primer uso
1. Abre Chiptone → **Dar acceso a notificaciones** → activa Chiptone Owner.
2. En WhatsApp: Ajustes → Notificaciones → Tono → **Ninguno** (deja la vibración).
3. Xiaomi/HyperOS: Inicio automático **activado** y batería **Sin restricciones**.
4. Prueba con **▶ PROBAR**.

## Notas
- `app/dev.keystore` es una llave de desarrollo para instalar por APK. Para Google Play se usa una llave de subida aparte (no subirla al repo).
- La app no lee el contenido de los mensajes; solo detecta qué app publicó la notificación.
- Lógica musical portada del prototipo web "Leitmotiv 8-bit" (ver especificación en el proyecto Chiptone).
