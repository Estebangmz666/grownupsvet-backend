# Botones de escritorio para el monitoreo

Se instalaron tres accesos directos en el escritorio de Windows. Se usan con **doble clic**:

- **Encender monitoreo GrownupsVet**: inicia Prometheus y Grafana locales, comprueba que estén listos y abre el panel en el navegador.
- **Apagar monitoreo GrownupsVet**: detiene solamente esos procesos de monitoreo; conserva su historial y configuración.
- **Abrir panel GrownupsVet**: abre el Grafana local si está encendido. Si está apagado, muestra un aviso; primero hay que usar Encender.

La API permanece accesible cuando se apaga el monitoreo. Estos botones no modifican Cloud Run, escalado, CPU, instancias mínimas o costo base del backend. Apagar sí interrumpe los scrapes y la recolección de métricas de este equipo.

Panel: <http://127.0.0.1:3000/d/grownupsvet-observability>. No es necesario iniciar sesión para consultarlo como Viewer local. La ventana PowerShell se mantiene oculta; si una acción falla se muestra un aviso genérico y se guarda el resultado saneado en `%USERPROFILE%\.grownupsvet\monitoring\controls\last-action.json`.

## Instalación y reversión

Desde la raíz del backend, con PowerShell 7.4 o posterior:

```powershell
.\scripts\observability\local\Install-LocalMonitoringShortcuts.ps1

# Retira exclusivamente los accesos propios que todavía coincidan con el registro.
.\scripts\observability\local\Install-LocalMonitoringShortcuts.ps1 -Uninstall
```

La instalación resuelve el escritorio real de Windows, comprueba que no se sobrescriba un acceso ajeno y relee los campos de cada `.lnk` mediante COM. La reversión mantiene procesos, datos y secretos; no desinstala el monitoreo. Si un acceso fue cambiado para apuntar a otra aplicación, se rechaza su reemplazo/eliminación automática.

Los accesos ejecutan una copia estable de los helpers bajo `%USERPROFILE%\.grownupsvet\monitoring\controls\versions`, con ACL usuario/SYSTEM y hashes por archivo. No necesitan que este checkout o rama sigan abiertos. Antes de ejecutar Start/Stop se comprueba la integridad de la copia y del PowerShell firmado por Microsoft registrado al instalar. Después de sustituir/eliminar ese ejecutable de PowerShell, reinstalar los accesos desde una sesión confiable actual.

La parada reutiliza la comprobación existente de PID, ejecutable y hora de inicio; no detiene por nombre/puerto ni cambia procesos ajenos. Un mutex impide acciones simultáneas de los botones. No se añadieron servicios, tareas, autostart, SDK o interfaz del frontend. La copia estable del respaldo Render y su automatización permanecen intactas.

## Verificación del 25/10/2026

Antes de instalar se confirmó que los tres nombres no existían. Se verificaron target, argumentos, directorio y estilo de los tres enlaces sin pulsarlos. El PowerShell usado tenía firma Microsoft válida. Se probaron las acciones desde la copia estable con navegador/diálogo desactivados para esa prueba:

1. Apagar terminó solamente los dos procesos identificados; no quedaron listeners en 3000/9090.
2. Durante esa parada, `GET /actuator/health/liveness` de la API desplegada respondió **200**.
3. Encender creó nuevos procesos de Prometheus/Grafana exclusivamente en `127.0.0.1:9090` y `127.0.0.1:3000`, con datasource/panel comprobados.
4. Abrir panel comprobó su disponibilidad sin lanzar una ventana durante la prueba. El comportamiento normal del acceso sí abre el navegador visible, solicitado por el usuario.
5. La reversión quitó exclusivamente los tres accesos registrados y la reinstalación los creó/verificó de nuevo; se conservaron datos y procesos. Una revisión independiente añadió la comprobación del archivo de estado contra reparse points antes de asignarlo y justo antes de escribir, evitando redirigir el reporte fuera del árbol privado.

El monitoreo quedó encendido. El scrape remoto del nuevo perfil sigue sujeto a su verificación posterior al despliegue; encender estos procesos locales no acredita por sí solo métricas de producción.

Plan previo: [plan-controles-monitoreo-local-2026-10-25.md](plan-controles-monitoreo-local-2026-10-25.md). Procedimiento completo de monitoreo: [monitoreo-local-windows.md](monitoreo-local-windows.md).
