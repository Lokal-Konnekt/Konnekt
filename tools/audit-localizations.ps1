param(
    [switch]$FailOnEnglishFallback
)

$ErrorActionPreference = 'Stop'
$roots = @('app/src/main/res', 'sharedUi/src/commonMain/composeResources')
$failures = [System.Collections.Generic.List[string]]::new()
$englishFallbacks = 0

function Get-ResourceMap([string]$file) {
    [xml]$document = [System.IO.File]::ReadAllText((Resolve-Path $file), [System.Text.Encoding]::UTF8)
    $map = @{}
    foreach ($node in $document.DocumentElement.ChildNodes) {
        if ($node.LocalName -in @('string', 'plurals', 'string-array')) {
            $key = $node.GetAttribute('name')
            if ($map.ContainsKey($key)) { $failures.Add("$file has duplicate key $key") }
            $map[$key] = $node
        }
    }
    return $map
}

function Get-Arguments([string]$value) {
    # Android positional arguments may be reordered by translators.
    $matches = [regex]::Matches($value, '%(?:\d+\$)?[sdfoxegac]')
    return @( $matches | ForEach-Object { $_.Value } | Sort-Object )
}

foreach ($root in $roots) {
    $source = Get-ResourceMap "$root/values/strings.xml"
    $files = Get-ChildItem $root -Directory -Filter 'values-*' |
        Where-Object { Test-Path (Join-Path $_.FullName 'strings.xml') }
    foreach ($folder in $files) {
        $file = Join-Path $folder.FullName 'strings.xml'
        $target = Get-ResourceMap $file
        foreach ($key in $source.Keys) {
            if (-not $target.ContainsKey($key)) {
                $failures.Add("$($folder.Name): missing $key in $root")
                continue
            }
            if ($source[$key].LocalName -ne $target[$key].LocalName) {
                $failures.Add("$($folder.Name): type differs for $key in $root")
                continue
            }
            if ($source[$key].LocalName -eq 'string') {
                $expected = (Get-Arguments $source[$key].InnerText) -join ','
                $actual = (Get-Arguments $target[$key].InnerText) -join ','
                if ($actual -ne $expected) {
                    $failures.Add("$($folder.Name): format arguments differ for $key in $root ($expected / $actual)")
                }
                if ($source[$key].InnerText.Length -gt 12 -and
                    $source[$key].InnerText -ceq $target[$key].InnerText) {
                    $englishFallbacks++
                }
            }
        }
        foreach ($key in $target.Keys) {
            if (-not $source.ContainsKey($key)) {
                $failures.Add("$($folder.Name): extra key $key in $root")
            }
        }
        Write-Output "$($folder.Name) ${root}: $($target.Count)/$($source.Count) keys"
    }
}

Write-Output "Untranslated long English strings across locale files: $englishFallbacks"
if ($FailOnEnglishFallback -and $englishFallbacks -gt 0) {
    $failures.Add("$englishFallbacks English strings remain in localized resources")
}
if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error $_ }
    exit 1
}
Write-Output 'Localization resource and format checks passed.'
