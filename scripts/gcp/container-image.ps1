function Get-LinuxAmd64ImageFromManifest {
    param(
        [Parameter(Mandatory)][string]$Image,
        [Parameter(Mandatory)][object]$Manifest
    )

    if ($Image -notmatch '^(?<repository>[^\s@]+)@sha256:[0-9a-f]{64}$') {
        throw "Container image must be pinned to a SHA-256 digest."
    }
    $repository = $Matches.repository
    if ($Manifest.schemaVersion -ne 2) {
        throw "Unsupported container manifest schema."
    }
    $imageTypes = @(
        "application/vnd.oci.image.manifest.v1+json",
        "application/vnd.docker.distribution.manifest.v2+json"
    )
    if ($Manifest.mediaType -in $imageTypes) {
        return $Image
    }
    if ($Manifest.mediaType -notin @(
        "application/vnd.oci.image.index.v1+json",
        "application/vnd.docker.distribution.manifest.list.v2+json"
    )) {
        throw "Unsupported container manifest media type: $($Manifest.mediaType)"
    }

    # Attestations use unknown/unknown and must never be selected as a runtime image.
    $candidates = @($Manifest.manifests | Where-Object {
        $_.platform.os -eq "linux" -and $_.platform.architecture -eq "amd64"
    })
    if ($candidates.Count -ne 1) {
        throw "Image index must contain exactly one linux/amd64 runtime manifest."
    }
    $candidate = $candidates[0]
    if ($candidate.mediaType -notin $imageTypes -or
        $candidate.digest -notmatch '^sha256:[0-9a-f]{64}$') {
        throw "Invalid linux/amd64 runtime manifest descriptor."
    }
    return "$repository@$($candidate.digest)"
}

function Resolve-LinuxAmd64Image {
    param([Parameter(Mandatory)][string]$Image)

    $manifestJson = @(& docker buildx imagetools inspect --raw $Image)
    if ($LASTEXITCODE -ne 0) {
        throw "Could not inspect the approved container manifest: $Image"
    }
    $manifest = ($manifestJson -join "`n") | ConvertFrom-Json
    return Get-LinuxAmd64ImageFromManifest -Image $Image -Manifest $manifest
}
