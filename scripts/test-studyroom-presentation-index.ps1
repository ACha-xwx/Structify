$ErrorActionPreference = 'Stop'

function Assert-Condition {
    param(
        [bool] $Condition,
        [string] $Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

$builder = Join-Path $PSScriptRoot 'build-studyroom-presentation-index.ps1'
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('studyroom-presentation-index-' + [Guid]::NewGuid().ToString('N'))
$renderRoot = Join-Path $tempRoot 'rendered'

try {
    $fixturePages = @(
        @{ Chapter = '第一章绪论'; Deck = '绪论讲义A'; Page = 1; ChapterId = '01'; DeckId = 'ch01-deck01' }
        @{ Chapter = '第一章绪论'; Deck = '绪论讲义A'; Page = 2; ChapterId = '01'; DeckId = 'ch01-deck01' }
        @{ Chapter = '第二章线性表'; Deck = '线性表讲义B'; Page = 3; ChapterId = '02'; DeckId = 'ch02-deck01' }
    )

    foreach ($page in $fixturePages) {
        $deckPath = Join-Path (Join-Path $renderRoot $page.Chapter) $page.Deck
        [System.IO.Directory]::CreateDirectory($deckPath) | Out-Null
        $pngPath = Join-Path $deckPath ('幻灯片{0}.PNG' -f $page.Page)
        $webpPath = [System.IO.Path]::ChangeExtension($pngPath, '.webp')
        [System.IO.File]::WriteAllBytes($pngPath, [byte[]]@())
        [System.IO.File]::WriteAllBytes($webpPath, [byte[]]@())
    }

    $null = & $builder -RenderRoot $renderRoot
    $firstDocument = Get-Content -LiteralPath (Join-Path $renderRoot 'slides.json') -Raw | ConvertFrom-Json
    $firstIds = @($firstDocument.slides | ForEach-Object { $_.id })
    $pngCount = @(Get-ChildItem -LiteralPath $renderRoot -Recurse -File -Filter '幻灯片*.PNG').Count

    Assert-Condition ($firstDocument.slides.Count -eq $pngCount) 'Expected exactly one slide record for each PNG.'
    Assert-Condition ($firstDocument.slides.Count -eq $fixturePages.Count) 'Unexpected slide record count.'
    Assert-Condition (($firstIds | Select-Object -Unique).Count -eq $firstIds.Count) 'Slide IDs must be unique.'

    foreach ($slide in $firstDocument.slides) {
        Assert-Condition ($slide.id -match '^ch\d{2}-deck\d{2}-s\d{3}$') "Invalid slide ID '$($slide.id)'."
        Assert-Condition ($slide.deckId -match '^ch\d{2}-deck\d{2}$') "Invalid deck ID '$($slide.deckId)'."
        Assert-Condition (-not [System.IO.Path]::IsPathRooted($slide.imagePath)) "Image path must be relative: '$($slide.imagePath)'."
        Assert-Condition (-not $slide.imagePath.Contains('\')) "Image path must use forward slashes: '$($slide.imagePath)'."
        Assert-Condition ($slide.imagePath.EndsWith('.PNG', [StringComparison]::Ordinal)) "Image path must retain the PNG filename: '$($slide.imagePath)'."
        Assert-Condition ($slide.lessonIds.Count -eq 0) "Slide '$($slide.id)' must not claim a lesson."
        Assert-Condition ($slide.shouldShow -eq $true) "Slide '$($slide.id)' should be shown."

        $matchingPage = $fixturePages | Where-Object {
            $_.DeckId -eq $slide.deckId -and $_.Page -eq $slide.slideNumber
        } | Select-Object -First 1
        Assert-Condition ($null -ne $matchingPage) "Unexpected slide record '$($slide.id)'."
        Assert-Condition ($slide.chapter -eq $matchingPage.ChapterId) "Wrong chapter ID for '$($slide.id)'."
        Assert-Condition ($slide.deckTitle -eq $matchingPage.Deck) "Wrong deck title for '$($slide.id)'."
        Assert-Condition ($slide.id -eq ('{0}-s{1:D3}' -f $matchingPage.DeckId, $slide.slideNumber)) "Wrong stable ID for '$($slide.id)'."

        $nativeRelativePath = [System.IO.Path]::Combine([string[]]$slide.imagePath.Split('/'))
        $pngPath = Join-Path $renderRoot $nativeRelativePath
        Assert-Condition (Test-Path -LiteralPath $pngPath -PathType Leaf) "PNG does not exist: '$($slide.imagePath)'."
        Assert-Condition (Test-Path -LiteralPath ([System.IO.Path]::ChangeExtension($pngPath, '.webp')) -PathType Leaf) "WebP sibling is missing for '$($slide.imagePath)'."
    }

    Assert-Condition ((@($firstDocument.slides.chapter | Sort-Object -Unique) -join ',') -ceq '01,02') 'Expected chapter IDs 01 and 02.'
    Assert-Condition ((@($firstDocument.slides.deckId | Sort-Object -Unique) -join ',') -ceq 'ch01-deck01,ch02-deck01') 'Expected deck IDs ch01-deck01 and ch02-deck01.'

    $plans = Get-Content -LiteralPath (Join-Path $renderRoot 'lesson-presentation-plans.json') -Raw | ConvertFrom-Json -AsHashtable
    Assert-Condition ($plans.lessons.Count -eq 0) 'The generated lesson map must be empty.'

    $null = & $builder -RenderRoot $renderRoot
    $secondDocument = Get-Content -LiteralPath (Join-Path $renderRoot 'slides.json') -Raw | ConvertFrom-Json
    $secondIds = @($secondDocument.slides | ForEach-Object { $_.id })
    Assert-Condition (($firstIds -join '|') -ceq ($secondIds -join '|')) 'Repeated generation changed slide IDs or ordering.'

    $missingTwinPath = Join-Path (Join-Path (Join-Path $renderRoot '第二章线性表') '线性表讲义B') '幻灯片3.webp'
    Remove-Item -LiteralPath $missingTwinPath
    $rejectedMissingTwin = $false
    try {
        $null = & $builder -RenderRoot $renderRoot
    }
    catch {
        $rejectedMissingTwin = $_.Exception.Message -match 'Missing WebP sibling'
    }
    Assert-Condition $rejectedMissingTwin 'The builder should reject a PNG without its WebP sibling.'

    $missingTwinWebp = [System.IO.Path]::ChangeExtension($missingTwinPath, '.webp')
    [System.IO.File]::WriteAllBytes($missingTwinWebp, [byte[]]@())
    $badNamePath = Join-Path (Join-Path (Join-Path $renderRoot '第一章绪论') '绪论讲义A') '幻灯片0.PNG'
    [System.IO.File]::WriteAllBytes($badNamePath, [byte[]]@())
    [System.IO.File]::WriteAllBytes([System.IO.Path]::ChangeExtension($badNamePath, '.webp'), [byte[]]@())
    $rejectedNoncanonicalName = $false
    try {
        $null = & $builder -RenderRoot $renderRoot
    }
    catch {
        $rejectedNoncanonicalName = $_.Exception.Message -match 'Noncanonical page filename'
    }
    Assert-Condition $rejectedNoncanonicalName 'The builder should reject a zero or otherwise noncanonical page filename.'
    Remove-Item -LiteralPath $badNamePath, ([System.IO.Path]::ChangeExtension($badNamePath, '.webp'))

    $duplicatePath = Join-Path (Join-Path (Join-Path $renderRoot '第一章绪论') '绪论讲义A') '幻灯片01.PNG'
    [System.IO.File]::WriteAllBytes($duplicatePath, [byte[]]@())
    [System.IO.File]::WriteAllBytes([System.IO.Path]::ChangeExtension($duplicatePath, '.webp'), [byte[]]@())
    $rejectedDuplicateNumber = $false
    $duplicateRejectionMessage = ''
    try {
        $null = & $builder -RenderRoot $renderRoot
    }
    catch {
        $duplicateRejectionMessage = $_.Exception.Message
        $rejectedDuplicateNumber = $_.Exception.Message -match 'Duplicate numeric page number'
    }
    Assert-Condition $rejectedDuplicateNumber "The builder should reject duplicate numeric page numbers. Actual: '$duplicateRejectionMessage'."

    $sourceRenderRoot = Join-Path $tempRoot 'source-rendered'
    $sourceRoot = Join-Path $tempRoot 'source-ppt'
    $sourceChapter = Join-Path $sourceRoot '第六章树和二叉树'
    $sourceDeck = Join-Path $sourceRenderRoot '第六章树和二叉树/树讲义'
    [System.IO.Directory]::CreateDirectory($sourceChapter) | Out-Null
    [System.IO.Directory]::CreateDirectory($sourceDeck) | Out-Null
    foreach ($pageNumber in 1..3) {
        $imageFile = Join-Path $sourceDeck "幻灯片$pageNumber.PNG"
        [System.IO.File]::WriteAllBytes($imageFile, [byte[]]@())
        [System.IO.File]::WriteAllBytes([System.IO.Path]::ChangeExtension($imageFile, '.webp'), [byte[]]@())
    }
    $sourceFile = Join-Path $sourceChapter '树讲义.pptx'
    $archive = [System.IO.Compression.ZipFile]::Open($sourceFile, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        $parts = @{
            'ppt/presentation.xml' = '<p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><p:sldIdLst><p:sldId id="256" r:id="second"/><p:sldId id="257" r:id="first"/><p:sldId id="258" r:id="blank"/></p:sldIdLst></p:presentation>'
            'ppt/_rels/presentation.xml.rels' = '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="first" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/><Relationship Id="second" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide2.xml"/><Relationship Id="blank" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="/ppt/slides/slide3.xml"/></Relationships>'
            'ppt/slides/slide1.xml' = '<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><p:cSld><p:spTree><p:sp><p:txBody><a:p><a:r><a:t>树的</a:t></a:r><a:r><a:t>遍历 &amp; 顺序</a:t></a:r></a:p><a:p><a:r><a:t>先序遍历</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:sld>'
            'ppt/slides/slide2.xml' = '<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><p:cSld><p:spTree><p:sp><p:txBody><a:p><a:r><a:t>正文在标题之前</a:t></a:r></a:p></p:txBody></p:sp><p:sp><p:nvSpPr><p:nvPr><p:ph type="ctrTitle"/></p:nvPr></p:nvSpPr><p:txBody><a:p><a:r><a:t>二叉树定义</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:sld>'
            'ppt/slides/slide3.xml' = '<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"/>'
        }
        foreach ($part in $parts.GetEnumerator()) {
            $writer = [System.IO.StreamWriter]::new($archive.CreateEntry($part.Key).Open())
            try { $writer.Write($part.Value) } finally { $writer.Dispose() }
        }
    }
    finally { $archive.Dispose() }
    $null = & $builder -RenderRoot $sourceRenderRoot -SourceRoot $sourceRoot
    $sourceDocument = Get-Content (Join-Path $sourceRenderRoot 'slides.json') -Raw | ConvertFrom-Json
    Assert-Condition ($sourceDocument.slides[0].title -ceq '二叉树定义') 'Title placeholder must take priority over earlier body text.'
    Assert-Condition ($sourceDocument.slides[0].rawText -ceq "正文在标题之前`n二叉树定义") 'First rendered page must use slide2 text in presentation order.'
    Assert-Condition ($sourceDocument.slides[0].imagePath.EndsWith('/幻灯片1.PNG')) 'Reordered source slide must associate with rendered page 1.'
    Assert-Condition ($sourceDocument.slides[1].title -ceq '树的遍历 & 顺序') 'Missing title placeholder must fall back to first paragraph, joining runs and decoding XML.'
    Assert-Condition ($sourceDocument.slides[1].rawText -ceq "树的遍历 & 顺序`n先序遍历") 'Source paragraphs must retain their text and order.'
    Assert-Condition ($sourceDocument.slides[2].title -ceq '' -and $sourceDocument.slides[2].rawText -ceq '') 'Blank source slides must not receive fabricated text.'
    foreach ($slide in $sourceDocument.slides) {
        Assert-Condition ($slide.semanticSummary -ceq '' -and $slide.concepts.Count -eq 0 -and $slide.lessonIds.Count -eq 0) 'Source extraction must not fabricate semantics.'
    }
    $indexPaths = @('slides.json', 'lesson-presentation-plans.json') | ForEach-Object { Join-Path $sourceRenderRoot $_ }
    $beforeHashes = @($indexPaths | ForEach-Object { (Get-FileHash -LiteralPath $_).Hash })
    Remove-Item -LiteralPath (Join-Path $sourceDeck '幻灯片3.PNG')
    $rejectedMismatch = $false
    try { $null = & $builder -RenderRoot $sourceRenderRoot -SourceRoot $sourceRoot }
    catch { $rejectedMismatch = $_.Exception.Message -match 'page count' }
    Assert-Condition $rejectedMismatch 'Source/rendered page count mismatch must be rejected.'
    $afterHashes = @($indexPaths | ForEach-Object { (Get-FileHash -LiteralPath $_).Hash })
    Assert-Condition (($beforeHashes -join '|') -ceq ($afterHashes -join '|')) 'Validation failure must preserve both indexes.'
    [System.IO.File]::WriteAllBytes((Join-Path $sourceDeck '幻灯片3.PNG'), [byte[]]@())
    Move-Item -LiteralPath (Join-Path $sourceDeck '幻灯片3.PNG') -Destination (Join-Path $sourceDeck '幻灯片4.PNG')
    Move-Item -LiteralPath (Join-Path $sourceDeck '幻灯片3.webp') -Destination (Join-Path $sourceDeck '幻灯片4.webp')
    $rejectedPageGap = $false
    try { $null = & $builder -RenderRoot $sourceRenderRoot -SourceRoot $sourceRoot }
    catch { $rejectedPageGap = $_.Exception.Message -match 'missing rendered page 3' }
    Assert-Condition $rejectedPageGap 'Equal counts with a rendered page gap must not associate the wrong source page.'
    $afterHashes = @($indexPaths | ForEach-Object { (Get-FileHash -LiteralPath $_).Hash })
    Assert-Condition (($beforeHashes -join '|') -ceq ($afterHashes -join '|')) 'Page association failure must preserve both indexes.'
    Move-Item -LiteralPath (Join-Path $sourceDeck '幻灯片4.PNG') -Destination (Join-Path $sourceDeck '幻灯片3.PNG')
    Move-Item -LiteralPath (Join-Path $sourceDeck '幻灯片4.webp') -Destination (Join-Path $sourceDeck '幻灯片3.webp')
    Move-Item -LiteralPath $sourceFile -Destination (Join-Path $sourceChapter '树讲义-other.pptx')
    $rejectedMissingSource = $false
    try { $null = & $builder -RenderRoot $sourceRenderRoot -SourceRoot $sourceRoot }
    catch { $rejectedMissingSource = $_.Exception.Message -match 'source deck' }
    Assert-Condition $rejectedMissingSource 'Source deck resolution must require the exact basename.'
    $afterHashes = @($indexPaths | ForEach-Object { (Get-FileHash -LiteralPath $_).Hash })
    Assert-Condition (($beforeHashes -join '|') -ceq ($afterHashes -join '|')) 'Missing source must preserve both indexes.'

    Write-Output 'PASS: Existing image-only checks plus source text, relationship order, title fallback, blank slides, exact source matching, and safe mismatch rejection verified.'
}
finally {
    if (Test-Path -LiteralPath $tempRoot) {
        $resolvedTempRoot = [System.IO.Path]::GetFullPath($tempRoot)
        $resolvedTempBase = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
        if (-not $resolvedTempRoot.StartsWith($resolvedTempBase, [StringComparison]::OrdinalIgnoreCase) -or -not [System.IO.Path]::GetFileName($resolvedTempRoot).StartsWith('studyroom-presentation-index-', [StringComparison]::Ordinal)) {
            throw "Refusing to remove a directory outside the generated test root: '$resolvedTempRoot'."
        }
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}
