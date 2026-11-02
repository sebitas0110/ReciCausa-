# ReciCausa v0.5.0 – Sprint 5 (Supabase, puntos de reciclaje, mapa y ubicación)

## Para ejecutar
1. **Base de datos**: Supabase > SQL Editor > pegar `docs/recicausa_supabase.sql` > Run (se puede repetir sin problema).
2. **Authentication > Providers > Email**: desactivar *Confirm email* (si no, tras registrarse hay que confirmar el correo).
3. **Mapa**: usa OpenStreetMap (osmdroid); no necesita clave ni configuración. Requiere internet para descargar las teselas.
4. Abrir en Android Studio, *Sync Gradle* y ejecutar. Pruebas: `./gradlew testDebugUnitTest`.

## Credenciales de Supabase (en `app/build.gradle.kts`, como `BuildConfig`)
- URL: `https://bmtfohrwetfulwasaypv.supabase.co`
- Clave publishable (`sb_publishable_…`): pública por diseño; la seguridad la dan las políticas RLS del script.
- **Nunca** pongas la clave `secret`/`service_role` en la app.

## Qué se agregó (semana 11 del sílabo)
| Tema del sílabo | Implementación |
|---|---|
| API REST con Retrofit + Coroutines + JSON | `data/remote/` (`SupabaseApi`, DTOs, `HeadersInterceptor`, `TokenAuthenticator`) |
| CRUD con manejo de errores y conectividad | `puntos_reciclaje` (POST/GET/PATCH/DELETE), `actividades` (upsert/GET/DELETE), `ApiResult`/`safeApiCall`, `ConnectivityObserver` |
| Autenticación / nube (en lugar de Firebase) | Supabase Auth (`RemoteAuthRepository`) + PostgREST |
| Mapa / geolocalización | OpenStreetMap con osmdroid: marcadores y selector de ubicación (`ui/components/OsmMap.kt`) |
| Geolocalización y permisos | Fused Location, permiso pedido solo al tocar "Cerca de mí" |
| Privacidad y seguridad | RLS por usuario; ubicación no se guarda ni se envía; contraseña no se guarda en el teléfono |

Pantallas nuevas: **Puntos** (lista/mapa, filtro, cercanía), **Sugerir un punto**, **Mis sugerencias**, y el bloque **Respaldo en la nube** en *Avisos*.
