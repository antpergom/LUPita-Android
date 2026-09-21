# LUPita

App Android personal de análisis de contenido en pantalla. Vive como una burbuja flotante sobre
cualquier otra aplicación: se selecciona una zona de la pantalla dibujando sobre ella y se analiza
con una o varias herramientas a la vez.

**Herramientas**

- Análisis general — qué aparece, resumen, OCR, traducción, extracción de datos.
- Verificación de hechos — afirmaciones, fuentes, fechas, contradicciones.
- Detección de contenido generado por IA — señales en texto e imagen (C2PA, metadatos, artefactos).
- Investigación de entidades — personas, marcas, empresas y dominios a partir de fuentes públicas.

Un selector de profundidad (baja / media / alta) decide cuánto trabajo y cuánto gasto se autoriza
en cada análisis.

## Estado

En desarrollo temprano. Uso estrictamente personal: no se publica en Google Play, se instala un
único APK en el propio dispositivo.

## Seguridad

- Ningún secreto en el código, en git ni compilado en el APK.
- Las claves de los proveedores de IA se introducen a mano en la app y se guardan cifradas con
  Tink (AES-256-GCM) bajo una clave maestra que no sale del Android Keystore.
- El servicio de accesibilidad puede leer el contenido de cualquier app: lo capturado no se
  comparte automáticamente, y lo que se persiste está acotado de forma explícita.

## Licencia

Sin licencia de uso público. Proyecto personal.
