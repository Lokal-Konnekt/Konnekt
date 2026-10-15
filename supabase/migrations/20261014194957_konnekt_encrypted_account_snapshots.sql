create table public.konnekt_account_snapshots (
  user_id uuid primary key references auth.users(id) on delete cascade,
  encrypted_payload jsonb not null,
  updated_at timestamptz not null default now(),
  constraint konnekt_payload_shape check (
    jsonb_typeof(encrypted_payload) = 'object'
    and encrypted_payload ? 'salt'
    and encrypted_payload ? 'iv'
    and encrypted_payload ? 'ciphertext'
  )
);

alter table public.konnekt_account_snapshots enable row level security;

create policy konnekt_snapshots_read on public.konnekt_account_snapshots
  for select to authenticated using ((select auth.uid()) = user_id);
create policy konnekt_snapshots_insert on public.konnekt_account_snapshots
  for insert to authenticated with check ((select auth.uid()) = user_id);
create policy konnekt_snapshots_update on public.konnekt_account_snapshots
  for update to authenticated using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);
create policy konnekt_snapshots_delete on public.konnekt_account_snapshots
  for delete to authenticated using ((select auth.uid()) = user_id);

grant select, insert, update, delete on public.konnekt_account_snapshots to authenticated;
