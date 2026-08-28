<#
.SYNOPSIS
Загружает ключ подписи релиза в секреты GitHub, чтобы релиз собирался в Actions.

.DESCRIPTION
Читает `local.properties` и `.jks`, на который тот ссылается, и создаёт четыре
секрета репозитория: EATBEFORE_KEYSTORE_BASE64, EATBEFORE_STORE_PASSWORD,
EATBEFORE_KEY_ALIAS, EATBEFORE_KEY_PASSWORD. Значения нигде не печатаются.

Запускать один раз, на машине, где ключ ещё есть:

    powershell -ExecutionPolicy Bypass -File scripts\setup-release-secrets.ps1

Проверить, ничего не записывая, — добавить -WhatIf:

    powershell -ExecutionPolicy Bypass -File scripts\setup-release-secrets.ps1 -WhatIf

ВАЖНО: секреты GitHub нельзя прочитать обратно — это не резервная копия. Сам файл
`.jks` и пароль к нему сохраните отдельно (менеджер паролей, внешний диск).
Потеряете ключ — обновить уже установленные копии приложения будет нечем.
#>

[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$LocalProperties
)

$ErrorActionPreference = 'Stop'

# Не значением по умолчанию у параметра: при запуске через `powershell -File`
# $PSScriptRoot в блоке param ещё пуст, и Join-Path падает на пустом пути.
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $LocalProperties) {
    $LocalProperties = Join-Path $repoRoot 'local.properties'
}

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

$secrets = [ordered]@{
    EATBEFORE_KEYSTORE_BASE64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore))
    EATBEFORE_STORE_PASSWORD  = $props['release.storePassword']
    EATBEFORE_KEY_ALIAS       = $props['release.keyAlias']
    EATBEFORE_KEY_PASSWORD    = $props['release.keyPassword']
}

foreach ($secret in $secrets.GetEnumerator()) {
    if ($PSCmdlet.ShouldProcess($secret.Key, 'gh secret set')) {
        # --body, а не stdin: gh может оставить перевод строки в конце значения, а
        # лишний символ в пароле сорвёт подпись только на середине сборки в CI.
        gh secret set $secret.Key --body $secret.Value
        if ($LASTEXITCODE -ne 0) { throw "Не удалось записать секрет $($secret.Key)." }
    }
}

$version = (Select-String -Path (Join-Path $repoRoot 'app\build.gradle.kts') `
    -Pattern 'versionName = "([^"]+)"').Matches[0].Groups[1].Value

Write-Host ''
Write-Host 'Готово. Список секретов:  gh secret list'
Write-Host ''
Write-Host 'Проверить, что ключ работает, ничего не публикуя (соберёт и сверит отпечаток,'
Write-Host 'APK положит в артефакты запуска):'
Write-Host ''
Write-Host "    gh workflow run Release -f version=$version -f dry_run=true"
Write-Host '    gh run watch'
Write-Host ''
Write-Host 'Не забудьте сохранить сам .jks и пароль отдельно — из секретов их не достать.'
