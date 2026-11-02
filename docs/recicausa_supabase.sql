-- =====================================================================
-- ReciCausa · Esquema de Supabase (v2 · Sprint 5 · app v0.5.0)
-- Curso: Desarrollo de Aplicaciones Móviles · Proyecto final
--
-- Cómo usarlo: Supabase Dashboard > SQL Editor > New query > pegar todo > Run.
-- Es RE-EJECUTABLE: se puede correr más de una vez sin errores ni duplicar datos.
--
-- Qué crea:
--   perfiles          Datos públicos de la cuenta (nombre). La cuenta la gestiona Supabase Auth.
--   actividades       Respaldo en la nube de las actividades de reciclaje (CRUD por usuario).
--   puntos_reciclaje  Puntos de reciclaje de Trujillo (lectura pública; el usuario sugiere nuevos).
--   residuos_distrito Datos abiertos del MINAM por distrito (solo lectura).
--   puntos_cercanos() Función RPC opcional: puntos aprobados ordenados por distancia.
--   Bucket fotos-actividades (opcional, al final).
--
-- Seguridad: todas las tablas tienen RLS. La app usa la clave "publishable"
-- (sb_publishable_...), que es pública por diseño: lo que protege los datos son
-- estas políticas, no el secreto de la clave.
--
-- ANTES DE PROBAR LA APP (Authentication > Providers > Email):
--   * Desactiva "Confirm email" para que el registro inicie sesión de inmediato
--     (si queda activo, la app avisa "Confirma tu correo" y el usuario debe abrir el enlace).
-- =====================================================================

begin;

-- ---------------------------------------------------------------------
-- 0. Utilidad: actualizar la columna actualizado_en en cada UPDATE
-- ---------------------------------------------------------------------
create or replace function public.set_actualizado_en()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.actualizado_en := now();
  return new;
end;
$$;

-- ---------------------------------------------------------------------
-- 1. perfiles  (1 fila por cuenta de Supabase Auth)
--    El correo y la contraseña los gestiona Supabase Auth; la contraseña
--    nunca llega a esta tabla.
-- ---------------------------------------------------------------------
create table if not exists public.perfiles (
  id             uuid primary key references auth.users (id) on delete cascade,
  nombre         text not null check (char_length(btrim(nombre)) between 2 and 40),
  creado_en      timestamptz not null default now(),
  actualizado_en timestamptz not null default now()
);

drop trigger if exists perfiles_actualizado_en on public.perfiles;
create trigger perfiles_actualizado_en
  before update on public.perfiles
  for each row execute function public.set_actualizado_en();

-- Crea el perfil automáticamente al registrarse. La app envía el nombre como
-- metadato en el registro: {"data": {"nombre": "Ana"}}.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_nombre text;
begin
  v_nombre := left(btrim(coalesce(new.raw_user_meta_data ->> 'nombre', '')), 40);
  if char_length(v_nombre) < 2 then
    v_nombre := 'Usuario';
  end if;
  insert into public.perfiles (id, nombre) values (new.id, v_nombre)
  on conflict (id) do nothing;
  return new;
end;
$$;

revoke execute on function public.handle_new_user() from public, anon, authenticated;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------
-- 2. actividades  (equivale a la tabla Room `activities`)
--    local_id = id de la fila en Room; permite sincronizar sin duplicar:
--    la app hace upsert con ?on_conflict=user_id,local_id.
--    Reglas: RN02 (cantidad 1..50), nota <= 80, RN03 (puntos).
-- ---------------------------------------------------------------------
create table if not exists public.actividades (
  id             bigint generated always as identity primary key,
  user_id        uuid not null default auth.uid() references auth.users (id) on delete cascade,
  local_id       bigint,
  categoria_id   text not null check (categoria_id in (
                   'papel_carton', 'plastico', 'vidrio', 'metales',
                   'organicos', 'peligrosos', 'no_aprovechables')),
  cantidad       integer not null check (cantidad between 1 and 50),
  nota           text not null default '' check (char_length(nota) <= 80),
  puntos         integer not null check (puntos between 0 and 750),
  foto_path      text,      -- ruta dentro del bucket fotos-actividades (opcional; la app v0.5.0 no sube fotos)
  creado_en      timestamptz not null default now(),
  actualizado_en timestamptz not null default now(),
  unique (user_id, local_id)
);

create index if not exists actividades_user_creado_idx on public.actividades (user_id, creado_en desc);

drop trigger if exists actividades_actualizado_en on public.actividades;
create trigger actividades_actualizado_en
  before update on public.actividades
  for each row execute function public.set_actualizado_en();

-- ---------------------------------------------------------------------
-- 3. puntos_reciclaje  (lista y mapa de OpenStreetMap)
--    Lectura pública de los aprobados. Los usuarios sugieren puntos nuevos
--    (quedan 'pendiente', RN11) y solo el equipo los aprueba, desde el panel
--    de Supabase (el rol del panel no está sujeto a RLS).
-- ---------------------------------------------------------------------
create table if not exists public.puntos_reciclaje (
  id             bigint generated always as identity primary key,
  nombre         text not null check (char_length(btrim(nombre)) between 3 and 80),
  descripcion    text check (char_length(descripcion) <= 300),
  direccion      text not null check (char_length(btrim(direccion)) between 5 and 150),
  distrito       text not null default 'Trujillo',
  latitud        double precision not null check (latitud between -90 and 90),
  longitud       double precision not null check (longitud between -180 and 180),
  categorias     text[] not null check (
                   cardinality(categorias) >= 1
                   and categorias <@ array['papel_carton', 'plastico', 'vidrio', 'metales',
                                           'organicos', 'peligrosos', 'no_aprovechables']::text[]),
  horario        text check (char_length(horario) <= 120),
  telefono       text check (char_length(telefono) <= 20),
  fuente         text not null default 'comunidad'
                   check (fuente in ('comunidad', 'osm', 'minam', 'equipo')),
  estado         text not null default 'pendiente'
                   check (estado in ('pendiente', 'aprobado', 'rechazado')),
  creado_por     uuid default auth.uid() references auth.users (id) on delete set null,
  creado_en      timestamptz not null default now(),
  actualizado_en timestamptz not null default now()
);

create index if not exists puntos_estado_idx      on public.puntos_reciclaje (estado);
create index if not exists puntos_categorias_idx  on public.puntos_reciclaje using gin (categorias);
create index if not exists puntos_creado_por_idx  on public.puntos_reciclaje (creado_por);

drop trigger if exists puntos_actualizado_en on public.puntos_reciclaje;
create trigger puntos_actualizado_en
  before update on public.puntos_reciclaje
  for each row execute function public.set_actualizado_en();

-- ---------------------------------------------------------------------
-- 4. residuos_distrito  (CSV "Generación anual de residuos sólidos
--    domiciliarios y municipales", MINAM, datosabiertos.gob.pe)
--    Solo lectura. Se carga con Table Editor > Import data from CSV.
--    Ajusta nombres y unidades a las del diccionario de datos del MINAM.
--    (La app v0.5.0 aún no muestra esta tabla.)
-- ---------------------------------------------------------------------
create table if not exists public.residuos_distrito (
  id                      bigint generated always as identity primary key,
  ubigeo                  text not null check (ubigeo ~ '^[0-9]{6}$'),
  departamento            text not null,
  provincia               text not null,
  distrito                text not null,
  tipo_municipalidad      text,
  clasificacion_municipal text,
  poblacion_total         integer,
  poblacion_urbana        integer,
  poblacion_rural         integer,
  gpc_domiciliaria        numeric,   -- generación per cápita domiciliaria
  gen_dom_urbana_diaria   numeric,   -- generación domiciliaria urbana diaria
  gen_dom_urbana_anual    numeric,   -- generación domiciliaria urbana anual
  gen_municipal_diaria    numeric,   -- generación municipal diaria
  gen_municipal_anual     numeric,   -- generación municipal anual
  gpc_municipal           numeric,   -- generación per cápita municipal
  anio                    integer
);

create index if not exists residuos_ubigeo_idx    on public.residuos_distrito (ubigeo);
create index if not exists residuos_provincia_idx on public.residuos_distrito (provincia, distrito);

-- ---------------------------------------------------------------------
-- 5. RPC opcional: puntos aprobados cercanos (fórmula de Haversine, sin PostGIS)
--    POST /rest/v1/rpc/puntos_cercanos
--    {"p_lat": -8.1116, "p_lng": -79.0288, "p_radio_km": 5, "p_categoria": "plastico"}
--    Respeta RLS (security invoker).
--    NOTA: la app NO la usa: calcula la distancia en el teléfono para no enviar la
--    ubicación del usuario a ningún servidor (CA11.3). Queda disponible como API extra.
-- ---------------------------------------------------------------------
create or replace function public.puntos_cercanos(
  p_lat       double precision,
  p_lng       double precision,
  p_radio_km  double precision default 5,
  p_categoria text default null,
  p_limite    integer default 50
)
returns table (
  id          bigint,
  nombre      text,
  descripcion text,
  direccion   text,
  distrito    text,
  latitud     double precision,
  longitud    double precision,
  categorias  text[],
  horario     text,
  telefono    text,
  distancia_km double precision
)
language sql
stable
security invoker
set search_path = ''
as $$
  select t.id, t.nombre, t.descripcion, t.direccion, t.distrito, t.latitud, t.longitud,
         t.categorias, t.horario, t.telefono, t.distancia_km
  from (
    select p.*,
           2 * 6371 * asin(sqrt(least(1,
             sin(radians(p.latitud - p_lat) / 2) ^ 2
             + cos(radians(p_lat)) * cos(radians(p.latitud))
               * sin(radians(p.longitud - p_lng) / 2) ^ 2
           ))) as distancia_km
    from public.puntos_reciclaje p
    where p.estado = 'aprobado'
      and (p_categoria is null or p.categorias @> array[p_categoria])
  ) t
  where t.distancia_km <= p_radio_km
  order by t.distancia_km
  limit least(greatest(p_limite, 1), 100);
$$;

-- ---------------------------------------------------------------------
-- 6. Seguridad por fila (RLS)  ·  RNF04 / RN09: cada cuenta ve solo lo suyo
-- ---------------------------------------------------------------------
alter table public.perfiles          enable row level security;
alter table public.actividades       enable row level security;
alter table public.puntos_reciclaje  enable row level security;
alter table public.residuos_distrito enable row level security;

-- perfiles: cada usuario lee y edita solo el suyo (se crea por trigger)
drop policy if exists perfiles_select_propio on public.perfiles;
create policy perfiles_select_propio on public.perfiles
  for select to authenticated
  using (id = (select auth.uid()));

drop policy if exists perfiles_update_propio on public.perfiles;
create policy perfiles_update_propio on public.perfiles
  for update to authenticated
  using (id = (select auth.uid()))
  with check (id = (select auth.uid()));

-- actividades: CRUD completo, solo de las filas propias (RN09)
drop policy if exists actividades_select_propias on public.actividades;
create policy actividades_select_propias on public.actividades
  for select to authenticated
  using (user_id = (select auth.uid()));

drop policy if exists actividades_insert_propias on public.actividades;
create policy actividades_insert_propias on public.actividades
  for insert to authenticated
  with check (user_id = (select auth.uid()));

drop policy if exists actividades_update_propias on public.actividades;
create policy actividades_update_propias on public.actividades
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

drop policy if exists actividades_delete_propias on public.actividades;
create policy actividades_delete_propias on public.actividades
  for delete to authenticated
  using (user_id = (select auth.uid()));

-- puntos_reciclaje: todos ven los aprobados; cada usuario ve y gestiona sus
-- sugerencias mientras estén pendientes. No puede aprobarse a sí mismo (RN11).
drop policy if exists puntos_select on public.puntos_reciclaje;
create policy puntos_select on public.puntos_reciclaje
  for select to anon, authenticated
  using (estado = 'aprobado' or creado_por = (select auth.uid()));

drop policy if exists puntos_insert_sugerencia on public.puntos_reciclaje;
create policy puntos_insert_sugerencia on public.puntos_reciclaje
  for insert to authenticated
  with check (creado_por = (select auth.uid())
              and estado = 'pendiente'
              and fuente = 'comunidad');

drop policy if exists puntos_update_sugerencia on public.puntos_reciclaje;
create policy puntos_update_sugerencia on public.puntos_reciclaje
  for update to authenticated
  using (creado_por = (select auth.uid()) and estado = 'pendiente')
  with check (creado_por = (select auth.uid())
              and estado = 'pendiente'
              and fuente = 'comunidad');

drop policy if exists puntos_delete_sugerencia on public.puntos_reciclaje;
create policy puntos_delete_sugerencia on public.puntos_reciclaje
  for delete to authenticated
  using (creado_por = (select auth.uid()) and estado = 'pendiente');

-- residuos_distrito: lectura pública, sin escritura desde la app
drop policy if exists residuos_select_publico on public.residuos_distrito;
create policy residuos_select_publico on public.residuos_distrito
  for select to anon, authenticated
  using (true);

-- ---------------------------------------------------------------------
-- 7. Permisos de la API (explícitos; RLS decide qué filas)
-- ---------------------------------------------------------------------
grant usage on schema public to anon, authenticated;

grant select                         on public.puntos_reciclaje  to anon, authenticated;
grant insert, update, delete         on public.puntos_reciclaje  to authenticated;
grant select                         on public.residuos_distrito to anon, authenticated;
grant select, update                 on public.perfiles          to authenticated;
grant select, insert, update, delete on public.actividades       to authenticated;

grant execute on function public.puntos_cercanos(double precision, double precision, double precision, text, integer)
  to anon, authenticated;

commit;

-- =====================================================================
-- OPCIONAL A · Fotos de las actividades en Supabase Storage
-- (La app v0.5.0 NO sube fotos: se quedan en el teléfono. Esto deja el bucket listo
--  por si el equipo lo activa en una versión futura.)
-- Bucket privado; cada usuario solo accede a su carpeta <user_id>/...
-- =====================================================================
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('fotos-actividades', 'fotos-actividades', false, 2097152,
        array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do nothing;

drop policy if exists fotos_select_propias on storage.objects;
create policy fotos_select_propias on storage.objects
  for select to authenticated
  using (bucket_id = 'fotos-actividades'
         and (storage.foldername(name))[1] = (select auth.uid())::text);

drop policy if exists fotos_insert_propias on storage.objects;
create policy fotos_insert_propias on storage.objects
  for insert to authenticated
  with check (bucket_id = 'fotos-actividades'
              and (storage.foldername(name))[1] = (select auth.uid())::text);

drop policy if exists fotos_update_propias on storage.objects;
create policy fotos_update_propias on storage.objects
  for update to authenticated
  using (bucket_id = 'fotos-actividades'
         and (storage.foldername(name))[1] = (select auth.uid())::text)
  with check (bucket_id = 'fotos-actividades'
              and (storage.foldername(name))[1] = (select auth.uid())::text);

drop policy if exists fotos_delete_propias on storage.objects;
create policy fotos_delete_propias on storage.objects
  for delete to authenticated
  using (bucket_id = 'fotos-actividades'
         and (storage.foldername(name))[1] = (select auth.uid())::text);

-- =====================================================================
-- OPCIONAL B · Punto de PRUEBA para ver el mapa funcionando.
-- NO es un punto real. Bórralo antes de la sustentación:
--     delete from public.puntos_reciclaje where fuente = 'equipo' and nombre like 'PUNTO DE PRUEBA%';
-- Los puntos reales deben verificarse y cargarse con estado 'aprobado'.
-- Para APROBAR una sugerencia de un usuario (desde el SQL Editor):
--     update public.puntos_reciclaje set estado = 'aprobado' where id = <id>;
-- =====================================================================
insert into public.puntos_reciclaje
  (nombre, direccion, latitud, longitud, categorias, fuente, estado, creado_por)
select 'PUNTO DE PRUEBA (borrar)', 'Plaza de Armas, Trujillo', -8.1116, -79.0288,
       array['plastico', 'papel_carton'], 'equipo', 'aprobado', null
where not exists (
  select 1 from public.puntos_reciclaje where nombre = 'PUNTO DE PRUEBA (borrar)'
);
