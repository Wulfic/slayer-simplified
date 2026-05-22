$coordJson = Get-Content "src\main\resources\data\location_coordinates.json" -Raw | ConvertFrom-Json
$coordKeys = $coordJson.PSObject.Properties.Name | ForEach-Object { $_.ToLower() }
$allAliases = [System.Collections.Generic.List[string]]::new()
$coordJson.PSObject.Properties | ForEach-Object { if ($_.Value.aliases) { foreach ($a in $_.Value.aliases) { $allAliases.Add($a.ToLower()) } } }
$allKnown = ($coordKeys + $allAliases) | Sort-Object -Unique
$taskFiles = Get-ChildItem "src\main\resources\data\tasks" -Filter "*.json"
$missing = [System.Collections.Generic.Dictionary[string,System.Collections.Generic.List[string]]]::new()
foreach ($file in $taskFiles) {
    $task = Get-Content $file.FullName -Raw | ConvertFrom-Json
    if ($task.locations) {
        foreach ($loc in $task.locations) {
            if ($loc.ToLower() -notin $allKnown) {
                if (-not $missing.ContainsKey($loc)) { $missing[$loc] = [System.Collections.Generic.List[string]]::new() }
                $missing[$loc].Add($file.Name)
            }
        }
    }
}
if ($missing.Count -eq 0) {
    "All task location strings are covered by location_coordinates.json"
} else {
    "MISSING LOCATIONS ($($missing.Count) distinct):"
    foreach ($k in ($missing.Keys | Sort-Object)) { "  '$k' used in: $($missing[$k] -join ', ')" }
}
