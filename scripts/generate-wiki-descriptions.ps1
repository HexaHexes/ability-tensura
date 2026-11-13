<#
.SYNOPSIS
    Regenerates the bundled tensura.wiki.gg ability descriptions.

.DESCRIPTION
    The one generator behind BOTH refresh paths (owner's wiki pipeline,
    A + B): a human runs it before a release, and
    .github/workflows/wiki-descriptions.yml runs it weekly to open a
    refresh PR.

    Ids come from the MOD, never from the wiki: the three magic tags
    (aspectual/spiritual/summoning, read from ARR's jar - the file id
    pinned in build.gradle) plus BattlewillCatalog's ART_PATHS parsed
    from the source. The wiki's own id signals disagree with the mod
    too often to trust alone (the Analyze page's icon is
    Analyze_aspectual.png while the registered id is `analyze`), so
    each registered id is joined to a page by normalized page title
    first, normalized icon name second, and the checked-in overrides
    table last. Pages come from the Abilities page's category
    sub-pages - Magics and Battlewills, the ids the foxablaze wiki mod
    does not carry, per TensuraAbilityCatalog's precedence.

    The prose is the text between the infobox and the first section
    heading. The run fails loudly below -MinimumEntries so a markup
    change can never ship a gutted file: the cron goes red instead, and
    ids no page covered are listed so a bridge gap is visible rather
    than silent.

    The output pins the shipped shape byte-for-byte: UTF-8 with BOM, LF
    endings, ordinal-sorted keys, and PS 5.1 ConvertTo-Json's value
    escaping (EscapeHtml) reimplemented here so a local Windows
    PowerShell run and the CI's pwsh emit identical bytes.

.PARAMETER OutFile
    Where to write; defaults to the shipped resource.

.PARAMETER MinimumEntries
    Fail below this many parsed abilities (a markup-break tripwire).

.PARAMETER Source
    The wiki base the weekly cron fetches.

.PARAMETER Categories
    The Abilities sub-pages whose navboxes list the ability pages.

.PARAMETER Namespace
    The id namespace every key lives in (the mod's namespace).

.PARAMETER ModJar
    Path to ARR's jar; defaults to the Gradle cache for the coordinate
    pinned in build.gradle, downloading it when the cache misses.
#>
[CmdletBinding()]
param(
    [string]$OutFile,
    [int]$MinimumEntries = 100,
    [string]$Source = "https://tensura.wiki.gg",
    [string[]]$Categories = @("Abilities/Magics", "Abilities/Battlewills"),
    [string]$Namespace = "tensura",
    [string]$ModJar
)

$ErrorActionPreference = "Stop"

# Resolved in the body, not the param default: Windows PowerShell 5.1
# leaves $PSScriptRoot empty inside a CmdletBinding default under
# powershell -File (the CI's in-process pwsh run is unaffected either
# way, and an explicit -OutFile always wins).
if (-not $OutFile) {
    $OutFile = "$PSScriptRoot/../src/main/resources/assets/ability_tensura/wiki_descriptions.json"
}

# wiki.gg answers 403 to Invoke-WebRequest's header set on both shells;
# curl gets through with a browser header set. Windows ships curl.exe
# (and PowerShell 5.1 aliases `curl` to Invoke-WebRequest - hence the
# .exe there), every GitHub runner ships plain curl.
$UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

$CurlCommand = $null
foreach ($candidate in @('curl.exe', 'curl')) {
    $command = Get-Command $candidate -ErrorAction SilentlyContinue
    if ($null -ne $command -and $command.CommandType -ne 'Alias') {
        $CurlCommand = $candidate
        break
    }
}
if (-not $CurlCommand) {
    throw "curl not found - it ships with Windows 10+ and every GitHub runner."
}

$RepoRoot = Split-Path -Parent $PSScriptRoot

function Invoke-ApiJson {
    param([string]$Query, [int]$TimeoutSec = 60)
    $url = "$Source/api.php?$Query"
    $temp = [System.IO.Path]::GetTempFileName()
    try {
        $null = & $CurlCommand -sS -L -f -o $temp --max-time $TimeoutSec -A $UserAgent $url
        if ($LASTEXITCODE -ne 0) {
            throw "fetch failed (curl exit $LASTEXITCODE): $url"
        }
        return Get-Content $temp -Raw -Encoding UTF8 | ConvertFrom-Json
    } finally {
        Remove-Item $temp -Force -ErrorAction SilentlyContinue
    }
}

function ConvertTo-QueryString {
    param([System.Collections.Specialized.OrderedDictionary]$Parameters)
    $parts = foreach ($key in $Parameters.Keys) {
        "$key=$([uri]::EscapeDataString([string]$Parameters[$key]))"
    }
    return ($parts -join '&')
}

# One key space for icon names, page titles and registered ids: lower
# case, every run of non-alphanumerics folded to a single underscore,
# underscores trimmed at the ends.
function Get-NormalKey {
    param([string]$Value)
    $key = [regex]::Replace($Value.ToLowerInvariant(), '[^a-z0-9]+', '_')
    return $key.Trim('_')
}

function Get-CategoryLinks {
    param([string]$Category)
    $titles = [System.Collections.Generic.List[string]]::new()
    $seen = @{}
    $continue = [ordered]@{}
    do {
        $query = [ordered]@{
            action = 'query'
            prop = 'links'
            titles = $Category
            pllimit = 'max'
            redirects = ''
            format = 'json'
            formatversion = '2'
        }
        foreach ($key in $continue.Keys) { $query[$key] = $continue[$key] }
        $response = Invoke-ApiJson (ConvertTo-QueryString $query)
        foreach ($page in $response.query.pages) {
            foreach ($link in $page.links) {
                if ($link.ns -ne 0) { continue } # Template/Category/Module noise
                $key = Get-NormalKey $link.title
                if (-not $seen.ContainsKey($key)) {
                    $seen[$key] = $true
                    $titles.Add($link.title)
                }
            }
        }
        $continue = [ordered]@{}
        if ($response.continue) {
            foreach ($property in $response.continue.PSObject.Properties) {
                $continue[$property.Name] = $property.Value
            }
        }
    } while ($continue.Count -gt 0)
    return ,$titles
}

function Get-PageContents {
    param([string[]]$PageTitles)
    $contents = [ordered]@{}
    for ($offset = 0; $offset -lt $PageTitles.Count; $offset += 25) {
        $last = [Math]::Min($offset + 24, $PageTitles.Count - 1)
        $chunk = $PageTitles[$offset..$last]
        $continue = [ordered]@{}
        do {
            $query = [ordered]@{
                action = 'query'
                prop = 'revisions'
                rvslots = 'main'
                rvprop = 'content'
                titles = ($chunk -join '|')
                redirects = ''
                format = 'json'
                formatversion = '2'
            }
            foreach ($key in $continue.Keys) { $query[$key] = $continue[$key] }
            $response = Invoke-ApiJson (ConvertTo-QueryString $query)
            # Titles I asked for that the API normalized or resolved
            # through a redirect all carry the same content.
            $aliases = @{}
            if ($response.query.normalized) {
                foreach ($pair in $response.query.normalized) {
                    $aliases[$pair.to] = $pair.from
                }
            }
            if ($response.query.redirects) {
                foreach ($pair in $response.query.redirects) {
                    $aliases[$pair.to] = $pair.from
                }
            }
            foreach ($page in $response.query.pages) {
                if ($page.missing -or -not $page.revisions) { continue }
                $content = $page.revisions[0].slots.main.content
                $contents[$page.title] = $content
                if ($aliases.ContainsKey($page.title)) {
                    $contents[$aliases[$page.title]] = $content
                }
            }
            $continue = [ordered]@{}
            if ($response.continue) {
                foreach ($property in $response.continue.PSObject.Properties) {
                    $continue[$property.Name] = $property.Value
                }
            }
        } while ($continue.Count -gt 0)
        Start-Sleep -Milliseconds 250
    }
    return $contents
}

# One ability page: the icon name and the page title as join keys, the
# prose from between the infobox and the first section heading (both
# wiki.gg infobox templates - {{Magic}} and {{battlewill}} - carry
# |image=).
function Get-AbilityPage {
    param([string]$Title, [string]$Content)
    $text = [regex]::Replace($Content, '<!--.*?-->', '', 'Singleline')
    $text = [regex]::Replace($text, '\[\[Category:[^\]]*\]\]', '', 'IgnoreCase')
    $text = [regex]::Replace($text, '\{\{DISPLAYTITLE:[^}]*\}\}', '', 'IgnoreCase')
    $text = [regex]::Replace($text, '\{\{DEFAULTSORT:[^}]*\}\}', '', 'IgnoreCase')
    $infoboxStart = $text.IndexOf('{{')
    if ($infoboxStart -lt 0) { return $null }
    $depth = 0
    $infoboxEnd = -1
    foreach ($match in [regex]::Matches($text.Substring($infoboxStart), '\{\{|\}\}')) {
        if ($match.Value -eq '{{') {
            $depth++
        } else {
            $depth--
            if ($depth -eq 0) {
                $infoboxEnd = $infoboxStart + $match.Index + 2
                break
            }
        }
    }
    if ($infoboxEnd -lt 0) { return $null }
    $infobox = $text.Substring($infoboxStart, $infoboxEnd - $infoboxStart)
    $image = [regex]::Match($infobox, '(?i)\|image\s*=\s*([^|\r\n}]+)')
    $iconKey = $null
    if ($image.Success -and
            $image.Groups[1].Value.Trim() -match '([A-Za-z0-9_\-]+)\.(?:png|jpe?g|gif)$') {
        $iconKey = Get-NormalKey $Matches[1]
    }
    $heading = $text.IndexOf('==', $infoboxEnd)
    if ($heading -lt 0) { return $null } # no section heading: unrecognised page
    $prose = $text.Substring($infoboxEnd, $heading - $infoboxEnd)
    # cut any leading template (evolution boxes etc.) before the prose
    while ($prose.TrimStart().StartsWith('{{')) {
        $templateStart = $prose.IndexOf('{{')
        $depth = 0
        $templateEnd = -1
        foreach ($match in [regex]::Matches($prose.Substring($templateStart), '\{\{|\}\}')) {
            if ($match.Value -eq '{{') {
                $depth++
            } else {
                $depth--
                if ($depth -eq 0) {
                    $templateEnd = $templateStart + $match.Index + 2
                    break
                }
            }
        }
        if ($templateEnd -lt 0) { break }
        $prose = $prose.Substring($templateEnd)
    }
    $prose = [regex]::Replace($prose, '\[\[([^\]|]+)\|([^\]]+)\]\]', '$2')
    $prose = [regex]::Replace($prose, '\[\[([^\]]+)\]\]', '$1')
    $prose = [regex]::Replace($prose, "'{2,3}", '')
    $prose = [regex]::Replace($prose, '<br\s*/?>', ' ', 'IgnoreCase')
    $prose = [regex]::Replace($prose, '<[^>]+>', ' ')
    $prose = [System.Net.WebUtility]::HtmlDecode($prose)
    $prose = [regex]::Replace($prose, '\s+', ' ').Trim()
    if ([string]::IsNullOrWhiteSpace($prose)) { return $null }
    return @{
        title = $Title
        iconKey = $iconKey
        titleKey = (Get-NormalKey $Title)
        prose = $prose
    }
}

# PS 5.1 ConvertTo-Json's value escaping (EscapeHtml), pinned by hand:
# quotes, backslash, the HTML set (< > & ') and control characters
# escape; everything else (including non-ASCII) rides through raw. PS 7's
# ConvertTo-Json would unicode-escape non-ASCII and desync a local run
# from the CI's.
function ConvertTo-JsonString {
    param([string]$Value)
    $builder = [System.Text.StringBuilder]::new($Value.Length + 8)
    [void]$builder.Append('"')
    foreach ($character in $Value.ToCharArray()) {
        switch ($character) {
            '"' { [void]$builder.Append('\"') }
            '\' { [void]$builder.Append('\\') }
            "`n" { [void]$builder.Append('\n') }
            "`r" { [void]$builder.Append('\r') }
            "`t" { [void]$builder.Append('\t') }
            default {
                if ([int]$character -lt 0x20 -or $character -eq '<' -or
                        $character -eq '>' -or $character -eq '&' -or
                        $character -eq [char]39) {
                    [void]$builder.Append(('\u{0:x4}' -f [int]$character))
                } else {
                    [void]$builder.Append($character)
                }
            }
        }
    }
    [void]$builder.Append('"')
    return $builder.ToString()
}

# Registered ids the wiki's icon/title signals do not reach - a page
# whose prose belongs to the id under a name the mod never used. Map of
# registered id -> the wiki page that carries its prose. The first run
# also settled possession_magic: the shipped file paired it with the
# Possession SKILL's prose (the mod's own lang confirms the skill owns
# that sentence under `possession`), so the magic id now points at its
# own page.
$Overrides = @{
    'dimension_cutter' = 'Dimensional Cutter'
    'eight_petals_flash' = 'Double Cherry Blossoms - Eight Petals Flash'
    'five_petals_thrust' = 'Plum Blossoms - Five Petals Thrust'
    'possession_magic' = 'Aspectual Possession'
}

function Resolve-ModJar {
    if ($ModJar) {
        if (-not (Test-Path $ModJar)) { throw "ModJar not found: $ModJar" }
        return $ModJar
    }
    $buildGradle = Join-Path $RepoRoot 'build.gradle'
    $gradleText = Get-Content $buildGradle -Raw
    if ($gradleText -notmatch "tensuraMod\s*=\s*'curse\.maven:([^:]+):(\d+)'") {
        throw "cannot parse the tensuraMod coordinate out of build.gradle"
    }
    $artifact = $Matches[1]
    $version = $Matches[2]
    $fileName = "$artifact-$version.jar"
    $gradleCache = Join-Path ([Environment]::GetFolderPath('UserProfile')) (
        ".gradle/caches/modules-2/files-2.1/curse.maven/$artifact/$version")
    if (Test-Path $gradleCache) {
        $cached = Get-ChildItem $gradleCache -Recurse -Filter $fileName -File |
            Select-Object -First 1
        if ($cached) {
            Write-Host "Using the Gradle-cached jar: $($cached.FullName)"
            return $cached.FullName
        }
    }
    $downloaded = Join-Path ([System.IO.Path]::GetTempPath()) $fileName
    if (-not (Test-Path $downloaded)) {
        $url = "https://cursemaven.com/curse/maven/$artifact/$version/$fileName"
        Write-Host "Downloading $url"
        $null = & $CurlCommand -sS -L -f -o $downloaded --max-time 300 -A $UserAgent $url
        if ($LASTEXITCODE -ne 0) {
            throw "jar download failed (curl exit $LASTEXITCODE): $url"
        }
    }
    return $downloaded
}

function Get-RegisteredIds {
    param([string]$JarPath)
    $ids = [System.Collections.Generic.List[string]]::new()
    $javaRoot = Join-Path $RepoRoot 'src/main/java/org/bitwisemadness/abilitytensura/catalog'

    # The magic tags TensuraAbilityCatalog registers (system "magic").
    $catalogText = Get-Content (Join-Path $javaRoot 'TensuraAbilityCatalog.java') -Raw
    $magicTags = [System.Collections.Generic.List[string]]::new()
    foreach ($match in [regex]::Matches($catalogText, 'new Category\("([^"]+)", "([^"]+)"')) {
        if ($match.Groups[2].Value -eq 'magic') { $magicTags.Add($match.Groups[1].Value) }
    }
    if ($magicTags.Count -eq 0) { throw "no magic categories parsed from TensuraAbilityCatalog" }

    # ZipFile ships loaded on pwsh; Windows PowerShell 5.1 needs the facade.
    if (-not ('System.IO.Compression.ZipFile' -as [type])) {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
    }
    $zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        foreach ($tag in $magicTags) {
            $entry = $zip.GetEntry(
                "data/tensura/tags/manascore_skill/skills/$tag.json")
            if ($null -eq $entry) { throw "tag missing from ARR's jar: $tag" }
            $reader = New-Object System.IO.StreamReader($entry.Open())
            try {
                $tagJson = $reader.ReadToEnd() | ConvertFrom-Json
            } finally {
                $reader.Close()
            }
            foreach ($value in $tagJson.values) {
                $raw = "$value"
                if (-not $raw.StartsWith("$($Namespace):")) {
                    throw "unexpected id in $tag (nested references are not handled): $raw"
                }
                $ids.Add($raw.Substring($Namespace.Length + 1))
            }
        }
    } finally {
        $zip.Dispose()
    }

    # Battlewills are hardcoded in BattlewillCatalog (no tag file).
    $battlewillText = Get-Content (Join-Path $javaRoot 'BattlewillCatalog.java') -Raw
    if ($battlewillText -notmatch 'ART_PATHS\s*=\s*List\.of\(([\s\S]*?)\);') {
        throw "cannot parse ART_PATHS out of BattlewillCatalog"
    }
    foreach ($match in [regex]::Matches($Matches[1], '"([^"]+)"')) {
        $ids.Add($match.Groups[1].Value)
    }

    return $ids
}

Write-Host "Discovering ability pages from: $($Categories -join ', ')"
$candidates = [System.Collections.Generic.List[string]]::new()
$seen = @{}
foreach ($category in $Categories) {
    foreach ($title in (Get-CategoryLinks $category)) {
        $key = Get-NormalKey $title
        if (-not $seen.ContainsKey($key)) {
            $seen[$key] = $true
            $candidates.Add($title)
        }
    }
}
Write-Host "Discovered $($candidates.Count) linked pages"

$contents = Get-PageContents $candidates.ToArray()

# Index every extractable page by both join keys, first page wins.
$pagesByIcon = @{}
$pagesByTitle = @{}
$proseByTitle = @{}
$skipped = [System.Collections.Generic.List[string]]::new()
foreach ($title in $candidates) {
    $content = $null
    if ($contents.Contains($title)) { $content = $contents[$title] }
    if ($null -eq $content) { $skipped.Add("$title (missing)"); continue }
    $page = Get-AbilityPage -Title $title -Content $content
    if ($null -eq $page) { $skipped.Add("$title (no prose)"); continue }
    $proseByTitle[$title] = $page.prose
    if ($page.iconKey -and -not $pagesByIcon.ContainsKey($page.iconKey)) {
        $pagesByIcon[$page.iconKey] = $title
    }
    if (-not $pagesByTitle.ContainsKey($page.titleKey)) {
        $pagesByTitle[$page.titleKey] = $title
    }
}
Write-Host "Extracted prose from $($proseByTitle.Count) pages"

$registeredIds = Get-RegisteredIds -JarPath (Resolve-ModJar)
Write-Host "Registered ids: $($registeredIds.Count) (magic tags + battlewills)"

$entries = [ordered]@{}
$uncovered = [System.Collections.Generic.List[string]]::new()
$normalizedIds = @{}
foreach ($id in $registeredIds) { $normalizedIds[(Get-NormalKey $id)] = $id }

foreach ($normalKey in ($normalizedIds.Keys | Sort-Object)) {
    $id = $normalizedIds[$normalKey]
    $title = $null
    # Title first: the wiki's canonical page name is the primary bridge
    # (Flight -> flight, Water Cutter (Aspectual) -> water_cutter_aspectual).
    # The icon name follows for pages the wiki retitled away from the
    # mod's id (Aspectual Strength's icon is strength_aspectual.png).
    if ($pagesByTitle.ContainsKey($normalKey)) {
        $title = $pagesByTitle[$normalKey]
    } elseif ($pagesByIcon.ContainsKey($normalKey)) {
        $title = $pagesByIcon[$normalKey]
    } elseif ($Overrides.ContainsKey($id)) {
        $title = $Overrides[$id]
        if (-not $proseByTitle.ContainsKey($title)) {
            $skipped.Add("$id (override page has no prose: $title)")
            $title = $null
        }
    }
    if ($null -eq $title) { $uncovered.Add($id); continue }
    $entries["$($Namespace):$id"] = $proseByTitle[$title]
}

if ($uncovered.Count -gt 0) {
    Write-Host ("No wiki prose for $($uncovered.Count) registered ids: " +
        ($uncovered -join ', '))
}
if ($entries.Count -lt $MinimumEntries) {
    throw ("Parsed only $($entries.Count) abilities (minimum $MinimumEntries) - " +
        "the pages' markup or the id bridge has changed. Fix the parser; " +
        "do not ship a gutted file.")
}

# The shipped shape: ordinal-sorted keys, PS 5.1 ConvertTo-Json value
# escaping, LF endings, UTF-8 with BOM, trailing newline.
$sorted = [string[]]$entries.Keys
[Array]::Sort($sorted, [System.StringComparer]::Ordinal)
$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add('{')
for ($index = 0; $index -lt $sorted.Count; $index++) {
    $key = $sorted[$index]
    $comma = if ($index -lt $sorted.Count - 1) { ',' } else { '' }
    $lines.Add(('    "{0}":  {1}{2}' -f $key, (ConvertTo-JsonString $entries[$key]), $comma))
}
$lines.Add('}')

$directory = Split-Path -Parent $OutFile
if ($directory -and -not (Test-Path $directory)) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
}
[System.IO.File]::WriteAllText($OutFile, (($lines -join "`n") + "`n"),
    [System.Text.UTF8Encoding]::new($true))

Write-Host "Wrote $($entries.Count) abilities to $OutFile"
if ($skipped.Count -gt 0) {
    Write-Host "Skipped $($skipped.Count) pages: $($skipped -join ', ')"
}
