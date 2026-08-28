<#
.SYNOPSIS
Загружает ключ подписи релиза в секреты GitHub, чтобы релиз собирался в Actions.

.DESCRIPTION
Читает `local.properties` и `.jks`, на который тот ссылается, и создаёт четыре
секрета репозитория: EATBEFORE_KEYSTORE_BASE64, EATBEFORE_STORE_PASSWORD,
EATBEFORE_KEY_ALIAS, EATBEFORE_KEY_PASSWORD. Значения нигде не печатаются.

Запускать один раз, на машине, где ключ ещё есть:

    powershell -ExecutionPolicy Bypass -File scripts\setup-release-secrets.ps1

ВАЖНО: секреты GitHub нельзя прочитать обратно — это не резервная копия. Сам файл
`.jks` и пароль к нему сохраните отдельно (менеджер паролей, внешний диск).
Потеряете ключ — обновить уже установленные копии приложения будет нечем.
#>

[CmdletBinding()]
param(
    [string]$LocalProperties = (Join-Path $PSScriptRoot '..\local.properties')
)

$ErrorActionPreference = 'Stop'

function Read-Properties([string]$path) {
    $map = @{}
    foreach ($line in Get-Content -LiteralPath $path -Encoding UTF8) {
        if ($line -match '^\s*[#!]') { continue }
        $i = $line.IndexOf('=')
        if ($i -lt 1) { continue }
        $key = $line.Substring(0, $i).Trim()
        $value = $line.Substring($i + 1).Trim()
        # В .properties двоеточие и обратный слэш экранируются: C\:/путь/к.jks
        $map[$key] = [regex]::Replace($value, '\\(.)', '$1')
    }
    return $map
}

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw 'Не найден gh (GitHub CLI). Установите его и выполните gh auth login.'
}
if (-not (Test-Path -LiteralPath $LocalProperties)) {
    throw "Не найден $LocalProperties — запускать из клона репозитория."
}

$props = Read-Properties $LocalProperties
foreach ($key in 'release.storeFile', 'release.storePassword', 'release.keyAlias', 'release.keyPassword') {
    if (-not $props.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($props[$key])) {
        throw "В local.properties нет $key — см. раздел «Подпись релиза» в README.md."
    }
}

$keystore = $props['release.storeFile']
if (-not (Test-Path -LiteralPath $keystore)) {
    throw "Файл ключа не найден: $keystore"
}

Write-Host "Ключ: $keystore"
Write-Host 'Загружаю секреты в репозиторий (значения не печатаются)...'

$base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore))

# --body, а не stdin: gh может оставить перевод строки в конце значения, а лишний
# символ в пароле сорвёт подпись только на середине сборки в CI.
gh secret set EATBEFORE_KEYSTORE_BASE64 --body $base64
gh secret set EATBEFORE_STORE_PASSWORD  --body $props['release.storePassword']
gh secret set EATBEFORE_KEY_ALIAS       --body $props['release.keyAlias']
gh secret set EATBEFORE_KEY_PASSWORD    --body $props['release.keyPassword']

Write-Host ''
Write-Host 'Готово. Проверить список:  gh secret list'
Write-Host 'Дальше релиз собирается на GitHub: Actions -> Release -> Run workflow.'
Write-Host ''
Write-Host 'Не забудьте сохранить сам .jks и пароль отдельно — из секретов их не достать.'
