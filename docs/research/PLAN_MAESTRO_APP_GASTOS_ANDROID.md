# Plan maestro: app Android local-first de presupuesto y gastos

**Fecha de investigación:** 2026-08-21  
**Contexto:** aplicación para usuarios individuales, dispositivos Android representativos y configuración regional configurable
**Objetivo:** registrar movimientos diarios, presupuestar por sobres (*pockets*) y reducir gradualmente la captura manual sin comprometer la integridad de los datos.

## Decisión ejecutiva

La vía más rápida, razonable y técnicamente sólida es una **app Android nativa, local-first y sin backend**:

- Kotlin + Jetpack Compose.
- Room como fuente única de verdad financiera.
- DataStore solo para preferencias.
- Sin login, cuenta remota, Supabase/Firebase, servidor, analítica ni permiso de Internet en el MVP.
- Registro manual de menos de 10 segundos mediante un shortcut `Nuevo gasto`.
- Presupuesto mensual por sobres digitales con rollover.
- Backup portátil cifrado y exportación CSV explícita.
- Captura por notificaciones únicamente en una fase posterior y siempre como **candidato pendiente de confirmar**.
- SMS directo fuera del alcance inicial.
- Open Banking regulado como opción futura mediante un proveedor AISP autorizado, no mediante conexión directa de una APK a los bancos.

La app puede ser utilizable en **5–8 días de trabajo enfocado**. Una versión personal endurecida, con recuperación probada, captura rápida y buena cobertura de pruebas, requiere aproximadamente **2–3 semanas**. La automatización de notificaciones agrega **1–2 semanas por la variabilidad de cada banco e idioma**.

## Supuestos que deben validarse

- La distribución inicial será controlada mediante un APK firmado en un dispositivo Android representativo; no se publicará inicialmente en Google Play.
- La moneda base es SAR y la zona presupuestaria es `Asia/Riyadh`.
- Las primeras pruebas usarán fixtures sintéticos para representar cuentas, efectivo y otros medios de pago.
- Las notificaciones bancarias reales todavía no han sido inspeccionadas.
- Los tiempos son estimaciones para una persona trabajando de forma enfocada y se ajustarán después del *spike* técnico.

## Comparación de métodos de captura

| Método | Esfuerzo | Automatización | Fiabilidad | Riesgo/permiso | Decisión |
|---|---:|---:|---:|---|---|
| Formulario manual | Bajo | Ninguna | Alta para lo registrado | Ninguno sensible | MVP |
| Shortcut fijado | Muy bajo | Ninguna | Alta | Ninguno sensible | MVP |
| Widget de acciones | Bajo–medio | Ninguna | Alta | Puede exponer saldos | Fase 2 |
| Quick Settings Tile | Bajo–medio | Ninguna | Alta | El usuario debe añadirlo | Fase 2 |
| Notificaciones bancarias | Medio–alto | Parcial | Media; depende del banco | Acceso amplio a notificaciones | Fase 3 |
| SMS directo | Alto | Parcial | Media | Permisos restringidos y datos muy sensibles | No recomendado |
| Importación CSV bancaria | Medio por formato | Por lotes | Alta si el archivo es consistente | Archivo sensible | Fase 4 opcional |
| Open Banking | Muy alto | Alta | Potencialmente alta | Proveedor autorizado, backend y consentimiento | Futuro condicional |

Android ofrece shortcuts estáticos, dinámicos y fijados; un shortcut puede abrir directamente una acción concreta sin permisos sensibles. Es el mecanismo correcto para el primer acceso rápido. [Android: app shortcuts](https://developer.android.com/develop/ui/compose/system/shortcuts).

## Experiencia de producto

### Navegación principal

- **Inicio:** estado del mes, disponible para asignar, alertas y pendientes.
- **Movimientos:** historial, búsqueda, filtros y conciliación.
- **Pockets:** asignado, gastado y disponible por sobre.
- **Análisis:** gasto neto por categoría y evolución del mes.
- Acción destacada **Añadir movimiento** accesible desde cualquier pantalla.

### Registro manual rápido

Orden propuesto:

1. Importe, con teclado numérico abierto.
2. Tipo: gasto, ingreso o transferencia.
3. Pocket/categoría, priorizando recientes y favoritas.
4. Cuenta, precargada con la última utilizada.
5. Guardar.

Fecha/hora actual, SAR y tipo `Gasto` se precargan. Comercio, nota, moneda extranjera y división de compra quedan en “Más detalles”. Al guardar se ofrece `Deshacer` y `Añadir otro`.

**Criterio de UX:** desde el shortcut hasta guardar un gasto frecuente deben pasar menos de 10 segundos y no más de cuatro decisiones obligatorias.

### Diseño de los pockets

En la UI, un pocket es una categoría de gasto presupuestable. No hace falta una entidad duplicada en el primer modelo: `Category` + `BudgetAllocation` representa el sobre y su asignación mensual.

Reglas:

- Solo se asigna dinero disponible.
- El disponible de un pocket es `asignado + rollover - gasto neto`.
- El saldo positivo puede pasar al mes siguiente.
- El sobregiro se permite, pero se muestra claramente y se cubre moviendo dinero desde otro pocket.
- Gastos anuales conocidos se modelan como *sinking funds* mensuales.
- “Misceláneos” existe, pero permanece visible para detectar abuso.

Una plantilla inicial puede incluir vivienda/servicios, supermercado, restaurantes/café, transporte, educación/software, salud, viajes, ocio, regalos/donaciones, emergencia e irregulares. El método de separar dinero por “pots” está descrito por [MoneyHelper](https://www.moneyhelper.org.uk/en/everyday-money/budgeting/managing-your-money-using-the-jam-jar-approach).

## Reglas financieras que no deben negociarse

| Evento | Registro correcto | Efecto en presupuesto |
|---|---|---|
| Compra | Gasto desde una cuenta y un pocket | Reduce ambos |
| Ingreso/estipendio | Entrada a una cuenta | Aumenta “Disponible para asignar” |
| Banco → ahorro | Dos lados enlazados | Transferencia; no ingreso/gasto |
| Retiro de ATM | Banco → efectivo | Transferencia; no gasto |
| Compra en efectivo | Gasto desde `Efectivo` | Reduce efectivo y pocket |
| Devolución | Crédito vinculado a la compra | Repone el pocket; no es ingreso |
| Comisión bancaria | Gasto real | Reduce pocket `Comisiones` |
| Cambio de divisa | Transferencia entre monedas | Solo comisión/diferencia es gasto |
| Corrección de saldo | Ajuste explícito y auditable | Nunca ocultarlo como gasto normal |

Las transferencias se guardan como dos movimientos invertidos con un mismo `transferGroupId`, dentro de una única transacción Room. Se excluyen de los reportes de gasto. Las devoluciones pueden ser parciales y reducen el gasto neto de su categoría, incluso si llegan en otro mes.

## Arquitectura técnica

```text
Shortcut / App / Widget / Tile
              │
              ▼
     Compose UI + ViewModels
      UiState ↓      ↑ eventos
              │
              ▼
       FinanceRepository
        ├── Room (fuente de verdad)
        ├── ImportCandidateStore
        └── Backup/Import adapter

 NotificationListenerService ──► parser por banco ──► candidatos
 DataStore ─────────────────────► preferencias no financieras
 WorkManager ───────────────────► recordatorios/mantenimiento
```

Android recomienda una capa de datos clara, repositorios, fuente única de verdad y flujo unidireccional; en una app offline-first, una base local es la fuente recomendada. [Arquitectura Android](https://developer.android.com/topic/architecture/recommendations), [data layer/offline-first](https://developer.android.com/topic/architecture/data-layer/offline-first), [UDF en Compose](https://developer.android.com/develop/ui/compose/architecture).

### Stack

- Kotlin y coroutines/Flow.
- Una Activity y Jetpack Compose/Material 3.
- Navigation Compose.
- Room con KSP y migraciones exportadas.
- Preferences DataStore para tema, recordatorios, bloqueo y paquetes bancarios permitidos.
- `androidx.biometric.BiometricPrompt` para bloqueo local opcional.
- WorkManager solo para trabajo persistente como recordatorios; nunca para guardar un gasto interactivo.
- Storage Access Framework para importar/exportar sin permisos generales de almacenamiento.
- Inyección manual. Hilt solo si el grafo crece lo suficiente para justificarlo.

Room aporta validación de consultas en compilación y migraciones sobre SQLite; DataStore es adecuado para datos pequeños y no ofrece integridad referencial ni actualizaciones parciales. [Room](https://developer.android.com/training/data-storage/room), [DataStore](https://developer.android.com/topic/libraries/architecture/datastore).

### Estructura de código inicial

Un solo módulo `app` evita sobrearquitectura. Paquetes sugeridos:

```text
app/
  data/db/
  data/repository/
  domain/model/
  domain/usecase/
  ui/home/
  ui/transactions/
  ui/pockets/
  ui/analysis/
  capture/notifications/
  capture/shortcuts/
  backup/
```

Crear casos de uso solo para lógica sustancial: confirmar candidato, transferir entre cuentas, cerrar mes, importar backup y conciliar. Los CRUD simples pueden vivir en el repositorio.

## Modelo de datos propuesto

### Principios

- Dinero como `Long` en unidades menores: `25.50 SAR = 2550` halalas; nunca `Float`/`Double`.
- Cada importe conserva un código ISO 4217; base `SAR`.
- Cada movimiento conserva instante UTC, `localDate` y `zoneId` para que un cambio de zona no mueva el mes presupuestario.
- Archivar cuentas/categorías en vez de borrarlas si tienen historial.
- Restricciones e índices en la base, no solo validaciones de UI.

ISO 4217 define los códigos monetarios y su relación con unidades menores. Para presentación localizada se usa ICU/CLDR. [ISO 4217](https://www.iso.org/iso-4217-currency-codes.html), [Android NumberFormatter](https://developer.android.com/reference/kotlin/android/icu/number/NumberFormatter).

### Entidades mínimas

**Account**

- `id`, `name`, `type` (`BANK`, `CASH`, `CARD`), `currencyCode`.
- `openingBalanceMinor`, `archived`, timestamps.

**Category**

- `id`, `name`, `kind` (`EXPENSE`, `INCOME`).
- `budgetable`, `iconKey`, `colorKey`, `sortOrder`, `archived`.

**Transaction**

- `id`, `accountId`, `categoryId?`.
- `type`: `EXPENSE`, `INCOME`, `TRANSFER`, `REFUND`, `ADJUSTMENT`.
- `amountMinor` positivo; el tipo define dirección.
- `currencyCode`, `occurredAtUtcMs`, `localDate`, `zoneId`.
- `merchant?`, `note?`, `transferGroupId?`, `refundOfId?`.
- `source`: `MANUAL`, `NOTIFICATION`, `IMPORT`, `OPEN_BANKING`.
- `status`: `PENDING`, `POSTED`, `RECONCILED`.
- `sourceFingerprint?` único, timestamps de auditoría.

**BudgetAllocation**

- `yearMonth`, `categoryId`, `allocatedMinor`, `currencyCode`, `rolloverPolicy`.
- Índice único `(yearMonth, categoryId, currencyCode)`.

**ImportCandidate** — se añade con notificaciones/importaciones

- Campos normalizados de importe, moneda, comercio, tipo y fecha.
- `sourcePackage`, `sourceFingerprint`, `parserId`, `confidence`, `state`.
- Estados `PENDING`, `CONFIRMED`, `IGNORED`, `DUPLICATE`, `FAILED`.

**MerchantRule** — posterior

- Comercio normalizado → categoría/cuenta sugeridas.
- Las reglas sugieren; nunca corrigen silenciosamente el historial.

### Evolución prevista

- Movimientos divididos: padre + hijos cuya suma exacta sea el padre; reportes cuentan uno u otros, nunca ambos.
- Recurrentes: plantilla que genera un borrador, no un gasto confirmado.
- Multi-moneda: importe original, importe SAR realmente cargado, tipo aplicado y comisión. Los reportes históricos no recalculan al tipo de cambio actual.
- Conciliación: sesión con fecha de corte y saldo declarado; modificar después un movimiento reconciliado requiere advertencia.

## Captura mediante notificaciones

`NotificationListenerService` puede recibir nuevas notificaciones publicadas por otras apps después de que el usuario habilite manualmente el acceso especial. No ofrece un historial completo de notificaciones descartadas. [Android: NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService).

### Pipeline

```text
Notificación nueva
  → filtro inmediato por packageName permitido
  → extraer title/text/textLines en memoria
  → parser versionado por banco e idioma
  → normalizar importe/comercio/tipo
  → fingerprint y deduplicación
  → ImportCandidate pendiente
  → usuario confirma/corrige
  → Transaction definitiva
```

### Controles obligatorios

- Pantalla de explicación clara antes de abrir Ajustes.
- Allowlist de paquetes bancarios elegidos por el usuario.
- Descartar inmediatamente contenido de cualquier otro paquete.
- Procesar localmente y no enviar datos ni logs.
- No guardar el texto crudo salvo modo de diagnóstico local, explícito y temporal.
- Parser independiente por banco, versión e idioma árabe/inglés.
- Idempotencia ante notificaciones actualizadas, agrupadas o repetidas.
- Distinguir compra, retiro, transferencia, devolución, ingreso, rechazo y simple aviso.
- Confirmación humana antes de afectar cuentas o pockets.
- Si no hay importe o cambia el formato, degradar de forma segura a captura manual.

Los callbacks del listener llegan en el hilo principal; el análisis y la escritura deben moverse a una coroutine de I/O. El servicio necesita `BIND_NOTIFICATION_LISTENER_SERVICE` y el usuario concede acceso desde Ajustes. [Referencia y declaración del listener](https://developer.android.com/reference/android/service/notification/NotificationListenerService), [ajustes específicos](https://developer.android.com/reference/android/provider/Settings#ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).

### Diferencias entre fabricantes

No se necesita un SDK específico de fabricante. Las políticas de ahorro de batería y suspensión pueden variar entre versiones y fabricantes. No se solicitará excluir la optimización de batería de forma preventiva; solo se documentará una excepción si una prueba física reproducible demuestra pérdida de eventos, midiendo también el consumo.

### Umbral para automatizar más

No se auto-confirma ningún candidato en la primera versión. Solo se evaluará auto-confirmación para un parser concreto después de al menos 100 ejemplos reales anonimizados, precisión medida ≥99.5%, cero confusión entre gastos/transferencias/rechazos y mecanismo de rollback. Aun así, el valor de esa automatización para un único usuario puede no justificar el riesgo.

## Por qué no SMS en el MVP

`READ_SMS`/`RECEIVE_SMS` son permisos restringidos y Google Play limita su uso; una app normalmente debe ser el handler predeterminado o calificar para una excepción revisada. Aunque existe un caso de gestión financiera basada en SMS, exige declaración y aprobación. [Android: permisos SMS](https://developer.android.com/reference/android/Manifest.permission#READ_SMS), [Google Play: SMS/Call Log policy](https://support.google.com/googleplay/android-developer/answer/10208820).

Leer SMS introduciría conversaciones, OTP, formatos variables y una superficie de seguridad desproporcionada. Tampoco se usarán notificaciones de aplicaciones de mensajería para rodear esas restricciones. El listener se limitará a las aplicaciones seleccionadas por el usuario.

## Open Banking regulado

Los servicios de información de cuentas permiten compartir datos con terceros bajo consentimiento. Cualquier integración debe cumplir las reglas del regulador y del proveedor autorizado aplicables a la región, incluidas seguridad, experiencia del cliente y certificación.

El acceso debe producirse mediante entidades supervisadas y conformes con el marco aplicable; no debe tratarse como una API abierta para que una APK se conecte directamente a cada banco. Las reglas deben exigir consentimiento, comunicación segura, acceso limitado y eliminación al retirar el consentimiento cuando corresponda.

### Consecuencia para este proyecto

La ruta realista sería integrar un AISP autorizado por el regulador correspondiente. Eso añade contrato, cobertura bancaria, backend seguro, redirects de consentimiento, tokens, revocación, retención, reconciliación, coste y obligaciones operativas. No se justifica hasta que los datos demuestren que el registro manual y las importaciones no cubren la necesidad.

**Prohibido:** scraping de banca web, guardar credenciales bancarias o simular el login del usuario.

## Seguridad, privacidad y recuperación

### Modelo de amenaza MVP

Se protege contra:

- otra app normal intentando leer los datos;
- acceso casual al teléfono desbloqueado;
- filtración por logs, exportaciones o backups accidentales;
- pérdida/cambio de dispositivo mediante backup explícito.

No se promete resistencia forense en un dispositivo rooteado y desbloqueado. El almacenamiento interno privado, sandbox de Android y cifrado del dispositivo son la base. Room no cifra por sí mismo toda la base.

### Medidas

- Sin permiso de Internet en el MVP.
- Sin SDKs de anuncios, analítica o crash reporting de terceros.
- BiometricPrompt opcional con biometría fuerte o credencial del dispositivo; es reautorización local, no login. [Android BiometricPrompt](https://developer.android.com/identity/sign-in/biometric-auth).
- Keystore solo para claves locales, no para guardar movimientos. [Android Keystore](https://developer.android.com/privacy-and-security/keystore).
- Componentes no exportados salvo los que Android exija y con permisos de binding correspondientes.
- Ningún importe, comercio, nota o texto de notificación en logs.
- Dependencias mínimas y actualizadas; no criptografía casera.
- Aviso prominente y consentimiento antes de habilitar notificaciones, aunque la distribución sea controlada.

### Backup

Auto Backup puede incluir bases privadas por defecto. Para datos financieros se recomienda:

- `android:allowBackup="false"`.
- `dataExtractionRules` explícitas para excluir base, archivos y preferencias tanto de nube como de transferencia entre dispositivos.
- Reglas legacy equivalentes si la versión mínima las necesita.
- Prueba en al menos un dispositivo físico representativo, ya que el comportamiento de transferencia entre dispositivos puede variar por versión/fabricante. [Android Auto Backup](https://developer.android.com/identity/data/autobackup).

La recuperación se realiza mediante Storage Access Framework, sin permisos generales de archivos. [Android SAF](https://developer.android.com/training/data-storage/shared/documents-files).

Formatos:

1. `.pocketbackup`: payload versionado y cifrado/autenticado, portable mediante contraseña de recuperación. La clave no puede depender solo de Keystore porque debe restaurarse en otro teléfono.
2. `.csv`: exportación legible para análisis; se advierte que no está cifrada y no sustituye al backup restaurable.

La importación limita tamaño, valida esquema/UUID/moneda/relaciones, muestra vista previa y se ejecuta dentro de una única transacción Room con rollback completo.

## Plan maestro por fases

### Fase 0 — Descubrimiento y spike, 1–2 días

**Trabajo**

- Confirmar versión de Android, configuración del fabricante y configuración de idioma.
- Enumerar bancos, tarjetas, efectivo y wallets usados.
- Recoger 10–20 ejemplos anonimizados por banco: compra, ATM, transferencia, devolución, rechazo e ingreso.
- Registrar si la notificación contiene importe, moneda, comercio, últimos dígitos y tipo.
- Definir saldos iniciales, pockets y política de rollover.
- Crear ADRs de arquitectura, backup y permisos.

**Salida**

- Matriz de cobertura por banco/idioma.
- Modelo de pockets aprobado.
- Decisión `GO/NO-GO` para listener, basada en evidencia.

### Fase 1 — MVP local utilizable, 5–8 días

**Trabajo**

- Crear proyecto Kotlin/Compose y build firmado.
- Room, DAOs, relaciones, constraints y primera migración.
- Onboarding: SAR, zona, cuenta bancaria, efectivo y saldos iniciales.
- Crear/editar/archivar categorías.
- Gasto, ingreso y transferencia enlazada.
- Pockets y asignación mensual con rollover.
- Lista, filtros y dashboard mensual.
- Shortcut `Nuevo gasto`.
- Exportación CSV y backup/restauración base.
- Unit tests de dinero, fechas, presupuesto y transferencias.

**Criterios de salida**

- Registro frecuente <10 segundos.
- Funciona en modo avión y sin permiso de Internet.
- Transferencias/retiros no aparecen como gasto.
- Totales de cuenta y pockets coinciden con fixtures conocidos.
- Backup → desinstalar → reinstalar → importar restaura exactamente los datos.
- APK release firmado instalado y probado en un dispositivo físico representativo.

### Fase 2 — Captura rápida y endurecimiento, 3–5 días

**Trabajo**

- Shortcut fijado y deep link interno único al formulario.
- Widget Glance solo con acciones `+ Gasto` y `+ Ingreso`; sin saldos por defecto.
- Quick Settings Tile opcional.
- Bloqueo biométrico y timeout.
- Reglas de backup/extracción verificadas.
- Backup cifrado final, importación transaccional y recuperación probada.
- TalkBack, texto grande, tema oscuro y objetivos táctiles de al menos 48 dp.

Android recomienda accesibilidad semántica en Compose y Material 3. [Accesibilidad Android](https://developer.android.com/develop/ui/compose/accessibility), [Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3).

**Criterios de salida**

- Los tres accesos abren el mismo flujo y respetan bloqueo del dispositivo.
- Ningún widget/shortcut revela información financiera sensible.
- Backup manipulado, truncado o con contraseña incorrecta falla sin escritura parcial.
- Flujos principales son utilizables con TalkBack y escala de fuente máxima.

### Fase 3 — Notificaciones asistidas, 1–2 semanas

**Trabajo**

- Consentimiento y acceso a Ajustes.
- NotificationListenerService y allowlist.
- `ImportCandidate`, fingerprints e idempotencia.
- Parsers versionados por banco/árabe/inglés.
- Bandeja de revisión: confirmar, corregir, ignorar.
- Reglas locales comercio → categoría.
- Métricas solo locales: cobertura, precisión y duplicados.
- Golden tests con fixtures anonimizados; nada de datos reales en Git.

**Criterios de salida**

- Cero lectura/persistencia de paquetes no permitidos.
- Ningún candidato afecta presupuesto sin confirmación.
- Notificaciones repetidas/actualizadas no crean duplicados.
- Parser falla cerrado cuando faltan importe, moneda o tipo.
- Compra, ATM, transferencia, devolución y rechazo se distinguen en los bancos probados.
- Prueba de varios días en un dispositivo físico representativo sin pérdida atribuible al ciclo de vida.

### Fase 4 — Calidad financiera, 1 semana incremental

**Trabajo**

- Recurrentes como borradores.
- Devoluciones parciales enlazadas.
- Movimientos divididos.
- Conciliación contra saldo/extracto.
- Importadores CSV para bancos concretos, si ofrecen exportación estable.
- Sinking funds y objetivos.

**Criterios de salida**

- La conciliación puede terminar con diferencia cero.
- Editar algo reconciliado advierte al usuario.
- Importar dos veces el mismo archivo no duplica.
- Reportes excluyen transferencias y evitan doble conteo de splits.

### Fase 5 — Open Banking, solo si hay caso probado

**Gate de entrada**

- La conciliación manual sigue costando demasiado o la cobertura automática es insuficiente.
- Existe AISP autorizado con cobertura de los bancos reales.
- Coste y contrato son aceptables para el proyecto.
- Se acepta introducir backend, operación y revisión legal/privacidad.

**Trabajo**

- Verificar registro y licencia vigentes del proveedor elegido ante el regulador correspondiente.
- Evaluar sandbox, bancos, SLA, precios, retención y exportabilidad.
- Diseñar backend y secretos; nunca embebidos en APK.
- Consentimiento, renovación, revocación y eliminación.
- Importar primero a candidatos y reconciliar con manual/notificaciones.

## Estrategia de pruebas

### Unitarias

- Aritmética monetaria, redondeo y exponentes de moneda.
- Límites de mes en `Asia/Riyadh`.
- Disponible por pocket y rollover.
- Transferencias, devoluciones y gasto neto.
- Parsers, fingerprints y deduplicación.
- Validación y round-trip de backup/importación.
- ViewModels con repositorios fake.

### Instrumentadas

- DAOs Room, claves foráneas, agregaciones y rollback.
- Migración desde cada versión soportada.
- Flujos Compose de crear, editar, eliminar, transferir y sobregirar.
- WorkManager y DataStore con entornos aislados.
- Listener con notificaciones sintéticas y fixtures anonimizados.

Room recomienda ejecutar pruebas de base en Android porque SQLite del host puede diferir. [Room testing](https://developer.android.com/training/data-storage/room/testing-db), [migrations](https://developer.android.com/training/data-storage/room/migrating-db-versions).

### Dispositivo físico representativo

- Modo avión, reinicio, proceso destruido y actualización de APK.
- Bloqueo/cancelación/fallback biométrico.
- Tema oscuro, TalkBack y texto máximo.
- Listener durante varios días, app sleeping y reinicio.
- Exportar, desinstalar, reinstalar e importar.
- Manifest release, componentes exportados y ausencia de datos en logs.

## Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Olvidar registrar | Historial incompleto | Shortcut, favoritas y revisión diaria |
| Parser cambia | Candidatos erróneos | Versionado, golden tests y fallo cerrado |
| Duplicados | Totales falsos | Fingerprint único e importación idempotente |
| Transferencia como gasto | Reportes falsos | Modelo enlazado y tests de dominio |
| Pérdida del teléfono | Pérdida de historial | Backup cifrado probado y recordatorio |
| CSV expuesto | Fuga de datos | Advertencia y backup cifrado por defecto |
| Acceso de notificaciones excesivo | Privacidad | Consentimiento, allowlist y procesamiento local |
| Scope creep | MVP tardío | Gates claros; SMS/Open Banking fuera |
| Firma APK perdida | Imposible actualizar sobre la instalación | Copia segura de keystore y contraseña |

## Métricas de éxito

- Tiempo mediano de registro manual <10 segundos.
- Al menos 90% de días con revisión/cierre breve durante el primer mes.
- Diferencia de conciliación mensual igual a cero o explicada por ajustes explícitos.
- Cero duplicados conocidos tras reimportar o recibir una actualización de notificación.
- 100% de backups de prueba restaurables en una instalación limpia.
- Notificaciones: precisión por parser medida; la cobertura se informa separadamente para no esconder eventos perdidos.

## Decisiones que se posponen conscientemente

- No backend ni sincronización.
- No login/cuenta de usuario.
- No publicación en Google Play.
- No SMS.
- No OCR de recibos.
- No IA/LLM para categorizar: reglas locales simples son explicables y suficientes al inicio.
- No auto-confirmación de notificaciones.
- No Open Banking hasta superar su gate de valor/coste.
- No cifrado SQL personalizado en el MVP; se reevalúa solo si cambia el modelo de amenaza.

## Próximo paso recomendado

Ejecutar la Fase 0 antes de escribir el proyecto final. Los únicos datos que faltan para cerrar el alcance son:

1. Bancos y proveedores que se incluirán en el alcance regional.
2. Ejemplos anonimizados de sus notificaciones en árabe/inglés.
3. Si se usará efectivo regularmente.
4. Pockets iniciales y reglas de rollover.
5. Política de distribución controlada y de backup.

Con esa evidencia se puede producir el backlog técnico definitivo, wireframes del flujo rápido y esquema Room v1 sin apostar a una automatización que el banco quizá no permita.

## Fuentes primarias y oficiales principales

- [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations)
- [Android offline-first data layer](https://developer.android.com/topic/architecture/data-layer/offline-first)
- [Room](https://developer.android.com/training/data-storage/room)
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
- [App shortcuts](https://developer.android.com/develop/ui/compose/system/shortcuts)
- [Quick Settings Tiles](https://developer.android.com/develop/ui/views/quicksettings-tiles)
- [App widgets](https://developer.android.com/develop/ui/views/appwidgets/overview)
- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- [Android permissions minimization](https://developer.android.com/privacy-and-security/minimize-permission-requests)
- [Auto Backup](https://developer.android.com/identity/data/autobackup)
- [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files)
- [BiometricPrompt](https://developer.android.com/identity/sign-in/biometric-auth)
- [Google Play SMS policy](https://support.google.com/googleplay/android-developer/answer/10208820)
- [ISO 4217](https://www.iso.org/iso-4217-currency-codes.html)

