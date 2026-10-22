param(
    [Parameter(Mandatory = $true)]
    [string] $RenderRoot,
    [string] $SourceRoot
)

$ErrorActionPreference = 'Stop'

function Read-PptxXml {
    param($Archive, [string] $PartName)

    $entry = $Archive.GetEntry($PartName)
    if ($null -eq $entry) { throw "Missing PPTX part '$PartName'." }
    $settings = [System.Xml.XmlReaderSettings]::new()
    $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver = $null
    $stream = $entry.Open()
    $reader = [System.Xml.XmlReader]::Create($stream, $settings)
    try {
        $document = [System.Xml.XmlDocument]::new()
        $document.XmlResolver = $null
        $document.Load($reader)
        return ,$document
    }
    finally { $reader.Dispose(); $stream.Dispose() }
}

function Get-PptxSlideText {
    param([string] $Path)

    $archive = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $presentation = Read-PptxXml $archive 'ppt/presentation.xml'
        $relationships = Read-PptxXml $archive 'ppt/_rels/presentation.xml.rels'
        $namespaces = [System.Xml.XmlNamespaceManager]::new($presentation.NameTable)
        $namespaces.AddNamespace('p', 'http://schemas.openxmlformats.org/presentationml/2006/main')
        $namespaces.AddNamespace('r', 'http://schemas.openxmlformats.org/officeDocument/2006/relationships')
        $namespaces.AddNamespace('a', 'http://schemas.openxmlformats.org/drawingml/2006/main')
        $relationshipNamespaces = [System.Xml.XmlNamespaceManager]::new($relationships.NameTable)
        $relationshipNamespaces.AddNamespace('rel', 'http://schemas.openxmlformats.org/package/2006/relationships')
        $byId = @{}
        foreach ($relationship in $relationships.SelectNodes('/rel:Relationships/rel:Relationship', $relationshipNamespaces)) {
            if ($byId.ContainsKey($relationship.Id)) { throw "Duplicate PPTX relationship '$($relationship.Id)'." }
            $byId[$relationship.Id] = $relationship
        }
        $records = [System.Collections.Generic.List[object]]::new()
        foreach ($slideId in $presentation.SelectNodes('/p:presentation/p:sldIdLst/p:sldId', $namespaces)) {
            $relationshipId = $slideId.GetAttribute('id', 'http://schemas.openxmlformats.org/officeDocument/2006/relationships')
            $relationship = $byId[$relationshipId]
            if ($null -eq $relationship -or $relationship.Type -cne 'http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide' -or $relationship.TargetMode -eq 'External') {
                throw "Invalid PPTX slide relationship '$relationshipId' in '$Path'."
            }
            $partUri = [Uri]::new([Uri]'http://pptx.local/ppt/presentation.xml', [string]$relationship.Target)
            if ($partUri.Host -cne 'pptx.local' -or $partUri.Query -or $partUri.Fragment) { throw "Invalid PPTX slide target '$($relationship.Target)'." }
            $partName = [Uri]::UnescapeDataString($partUri.AbsolutePath.TrimStart('/'))
            $slideXml = Read-PptxXml $archive $partName
            $paragraphs = @($slideXml.SelectNodes('//a:p', $namespaces) | ForEach-Object {
                $paragraphText = -join @($_.SelectNodes('.//a:t | .//a:br', $namespaces) | ForEach-Object {
                    if ($_.LocalName -eq 'br') { "`n" } else { $_.InnerText }
                })
                if (-not [string]::IsNullOrWhiteSpace($paragraphText)) { $paragraphText }
            })
            $titleParagraphs = @($slideXml.SelectNodes('//p:sp[p:nvSpPr/p:nvPr/p:ph[@type="title" or @type="ctrTitle"]]/p:txBody/a:p', $namespaces) | ForEach-Object {
                $paragraphText = -join @($_.SelectNodes('.//a:t | .//a:br', $namespaces) | ForEach-Object {
                    if ($_.LocalName -eq 'br') { "`n" } else { $_.InnerText }
                })
                if (-not [string]::IsNullOrWhiteSpace($paragraphText)) { $paragraphText }
            })
            $title = ''
            if ($titleParagraphs.Count -gt 0) { $title = $titleParagraphs -join "`n" }
            elseif ($paragraphs.Count -gt 0) { $title = $paragraphs[0] }
            $records.Add([pscustomobject]@{ Title = $title; RawText = $paragraphs -join "`n" })
        }
        return ,$records.ToArray()
    }
    finally { $archive.Dispose() }
}

function Sort-ByOrdinalName {
    param([object[]] $Items)

    $sorted = [System.Collections.Generic.List[object]]::new()
    foreach ($item in $Items) {
        $index = 0
        while ($index -lt $sorted.Count -and [string]::CompareOrdinal([string]$sorted[$index].Name, [string]$item.Name) -lt 0) {
            $index++
        }
        $sorted.Insert($index, $item)
    }
    return ,$sorted.ToArray()
}

$chapterIds = @{
    '第一章绪论' = '01'
    '第二章线性表' = '02'
    '第三章限定性线性表' = '03'
    '第五章数组和广义表' = '05'
    '第六章树和二叉树' = '06'
    '第七章图' = '07'
    '第八章查找' = '08'
    '第九章排序' = '09'
}

$root = (Resolve-Path -LiteralPath $RenderRoot).Path
$sourcePath = $null
if ($SourceRoot) { $sourcePath = (Resolve-Path -LiteralPath $SourceRoot).Path }
$slides = [System.Collections.Generic.List[object]]::new()
$deckCount = 0
$invariantCulture = [Globalization.CultureInfo]::InvariantCulture
$chapterFolders = Sort-ByOrdinalName -Items @(Get-ChildItem -LiteralPath $root -Directory)

foreach ($chapterFolder in $chapterFolders) {
    if (-not $chapterIds.ContainsKey($chapterFolder.Name)) {
        throw "No chapter ID mapping exists for '$($chapterFolder.Name)'"
    }

    $chapterId = $chapterIds[$chapterFolder.Name]
    $deckNumber = 0
    $deckFolders = Sort-ByOrdinalName -Items @(Get-ChildItem -LiteralPath $chapterFolder.FullName -Directory)
    foreach ($deckFolder in $deckFolders) {
        $deckNumber++
        $deckCount++
        $deckId = [string]::Format($invariantCulture, 'ch{0}-deck{1:D2}', $chapterId, $deckNumber)

        $pageFiles = [System.Collections.Generic.List[object]]::new()
        $seenPageNumbers = [System.Collections.Generic.HashSet[int]]::new()
        $candidates = Get-ChildItem -LiteralPath $deckFolder.FullName -File | Where-Object {
            $_.Name.StartsWith('幻灯片', [StringComparison]::Ordinal) -and
                $_.Name.EndsWith('.PNG', [StringComparison]::Ordinal)
        }

        foreach ($pageFile in $candidates) {
            $match = [regex]::Match($pageFile.Name, '^幻灯片([0-9]+)\.PNG$', [Text.RegularExpressions.RegexOptions]::CultureInvariant)
            if (-not $match.Success) {
                throw "Invalid page filename '$($pageFile.FullName)'; expected 幻灯片N.PNG with an ASCII page number."
            }

            $pageNumber = 0
            if (-not [int]::TryParse($match.Groups[1].Value, [Globalization.NumberStyles]::None, $invariantCulture, [ref]$pageNumber)) {
                throw "Invalid page number in '$($pageFile.FullName)'; page numbers must fit a positive 32-bit integer."
            }
            if ($pageNumber -le 0) {
                throw "Noncanonical page filename '$($pageFile.FullName)'; page numbers must be positive and have no leading zeroes."
            }
            $canonicalName = '幻灯片' + $pageNumber.ToString($invariantCulture) + '.PNG'
            if ($pageFile.Name -cne $canonicalName) {
                if (Test-Path -LiteralPath (Join-Path $deckFolder.FullName $canonicalName) -PathType Leaf) {
                    throw "Duplicate numeric page number $pageNumber in deck '$($deckFolder.FullName)'."
                }
                throw "Noncanonical page filename '$($pageFile.FullName)'; expected '$canonicalName'."
            }
            if (-not $seenPageNumbers.Add($pageNumber)) {
                throw "Duplicate numeric page number $pageNumber in deck '$($deckFolder.FullName)'."
            }

            $webpPath = [System.IO.Path]::ChangeExtension($pageFile.FullName, '.webp')
            if (-not (Test-Path -LiteralPath $webpPath -PathType Leaf)) {
                throw "Missing WebP sibling for '$($pageFile.FullName)'"
            }

            $pageFiles.Add([pscustomobject]@{ File = $pageFile; PageNumber = $pageNumber })
        }

        $sourceSlides = @()
        if ($sourcePath) {
            $sourceFile = Join-Path (Join-Path $sourcePath $chapterFolder.Name) ($deckFolder.Name + '.pptx')
            if (-not (Test-Path -LiteralPath $sourceFile -PathType Leaf)) { throw "Missing source deck '$sourceFile'." }
            $sourceSlides = Get-PptxSlideText -Path $sourceFile
            if ($sourceSlides.Count -ne $pageFiles.Count) {
                throw "Source/rendered page count mismatch for '$sourceFile': $($sourceSlides.Count) source slides, $($pageFiles.Count) rendered pages."
            }
            foreach ($expectedPage in 1..$sourceSlides.Count) {
                if ($sourceSlides.Count -gt 0 -and -not $seenPageNumbers.Contains($expectedPage)) {
                    throw "Source/rendered page count association mismatch for '$sourceFile': missing rendered page $expectedPage."
                }
            }
        }

        foreach ($pageRecord in ($pageFiles | Sort-Object PageNumber)) {
            $pageFile = $pageRecord.File
            $pageNumber = $pageRecord.PageNumber
            $relativePath = [System.IO.Path]::GetRelativePath($root, $pageFile.FullName).Replace('\', '/')
            $title = [string]::Format($invariantCulture, '{0} 第{1}页', $deckFolder.Name, $pageNumber)
            $rawText = ''
            if ($sourcePath) {
                $title = $sourceSlides[$pageNumber - 1].Title
                $rawText = $sourceSlides[$pageNumber - 1].RawText
            }
            $slides.Add([ordered]@{
                id = [string]::Format($invariantCulture, '{0}-s{1:D3}', $deckId, $pageNumber)
                deckId = $deckId
                deckTitle = $deckFolder.Name
                slideNumber = $pageNumber
                chapter = $chapterId
                title = $title
                rawText = $rawText
                speakerNotes = ''
                semanticSummary = ''
                teachingRole = ''
                teachingFocus = ''
                concepts = @()
                visualAnchors = @()
                shouldShow = $true
                lessonIds = @()
                imagePath = $relativePath
            })
        }
    }
}

$builtAt = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ', $invariantCulture)
$slidesDocument = [ordered]@{
    version = 1
    builtAt = $builtAt
    slides = @($slides.ToArray())
}
$plansDocument = [ordered]@{
    version = 1
    builtAt = $builtAt
    lessons = [ordered]@{}
}

$slidesJson = ConvertTo-Json -InputObject $slidesDocument -Depth 8
$plansJson = ConvertTo-Json -InputObject $plansDocument -Depth 8
[System.IO.File]::WriteAllText((Join-Path $root 'slides.json'), $slidesJson + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
[System.IO.File]::WriteAllText((Join-Path $root 'lesson-presentation-plans.json'), $plansJson + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))

Write-Output "Built $($slides.Count) slides across $deckCount decks under '$root'."
