$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "Нужен Docker (Docker Desktop). Установите и повторите."
}
docker compose -f docker-compose.engine-relay.yml up -d
Write-Host ""
Write-Host "Engine relay запущен. Скопируйте ключ из файла:"
Write-Host (Join-Path $root "engine-relay-data\id_ed25519.pub")
Write-Host ""
Write-Host "Для ПК в сети задайте перед запуском: `$env:RUC_ENGINE_RELAY='192.168.x.x:21117' и пересоздайте контейнеры."
