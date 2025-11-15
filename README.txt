Proyecto: Gestión de Contactos (Unidad 3)
Autor: Sebastián Henao

Archivos principales:
 - src/modelo/persona.java
 - src/modelo/personaDAO.java
 - src/controlador/logica_ventana.java
 - src/vista/ventana.java

Ejecución:
 - Importar proyecto en NetBeans o compilar con javac.
 - Ejecutar la clase vista.ventana (contiene main).

Funcionalidades principales:
 - Añadir / editar / eliminar contactos.
 - Búsqueda en tiempo real (no bloqueante).
 - Validación de duplicados en segundo plano.
 - Exportación a CSV mediante JFileChooser en background (no bloqueante).
 - Persistencia local en 'datosContactos.csv'.
 - Estadísticas actualizadas en tiempo real.
 - Idiomas (Español, Inglés, Francés) con textos básicos.

Notas:
 - Para detener hilos limpios: llamar a logica_ventana.shutdown() al cerrar la aplicación (opcional).
 - El archivo datosContactos.csv se ubicará en el directorio de trabajo de la app.