param([switch]$PrepareOnly,
    [string]$MinecraftHome=(Join-Path $env:APPDATA '.minecraft'),
    [string]$OneConfigCache='',
    [string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$taskProject=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskGame=Join-Path $taskProject ('smoke/run-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
$taskMinecraft=[IO.Path]::GetFullPath($MinecraftHome)
if(-not $OneConfigCache){$OneConfigCache=Join-Path $taskMinecraft 'OneConfig'}
$taskSeed=[IO.Path]::GetFullPath($OneConfigCache)
if(-not $JavaHome){throw 'Set JAVA_HOME to a JDK 8 installation or supply -JavaHome.'}
$taskJava=Join-Path $JavaHome 'bin/java.exe'
$taskBase=Get-Content -LiteralPath (Join-Path $taskMinecraft 'versions/1.8.9/1.8.9.json') -Raw | ConvertFrom-Json
$taskForge=Get-Content -LiteralPath (Join-Path $taskMinecraft 'versions/1.8.9-forge1.8.9-11.15.1.2318-1.8.9/1.8.9-forge1.8.9-11.15.1.2318-1.8.9.json') -Raw | ConvertFrom-Json
$taskClasspath=[Collections.Generic.List[string]]::new()
$taskClasspath.Add((Join-Path $taskMinecraft 'versions/1.8.9/1.8.9.jar'))
function Test-TaskRule($library){if(-not $library.rules){return $true};$allowed=$false;foreach($rule in $library.rules){if(-not $rule.os -or $rule.os.name -eq 'windows'){$allowed=$rule.action -eq 'allow'}};return $allowed}
foreach($library in @($taskBase.libraries)+@($taskForge.libraries)){
    if(-not(Test-TaskRule $library)){continue}
    if($library.downloads.artifact.path){$path=Join-Path $taskMinecraft ('libraries/'+$library.downloads.artifact.path)}
    elseif($library.downloads){continue}
    else{$parts=$library.name.Split(':');$suffix=if($parts.Count -gt 3){'-'+$parts[3]}else{''};$path=Join-Path $taskMinecraft ('libraries/'+$parts[0].Replace('.','/')+'/'+$parts[1]+'/'+$parts[2]+'/'+$parts[1]+'-'+$parts[2]+$suffix+'.jar')}
    if(-not(Test-Path -LiteralPath $path)){throw "Missing library: $path"}
    if(-not $taskClasspath.Contains($path)){$taskClasspath.Add($path)}
}
New-Item -ItemType Directory -Path $taskGame,(Join-Path $taskGame 'mods'),(Join-Path $taskGame 'natives'),(Join-Path $taskGame 'OneConfig') | Out-Null
Copy-Item -LiteralPath (Join-Path $taskProject 'build/libs/PartyMod-2.0.4.jar'),(Join-Path $taskProject 'build/libs/PartyModSmokeProbe-2.0.4.jar') -Destination (Join-Path $taskGame 'mods')
Copy-Item -LiteralPath (Join-Path $taskSeed 'OneConfig (1.8.9-forge).jar') -Destination (Join-Path $taskGame 'OneConfig')
foreach($dir in @('launchwrapper','temp')){if(Test-Path -LiteralPath (Join-Path $taskSeed $dir)){Copy-Item -LiteralPath (Join-Path $taskSeed $dir) -Destination (Join-Path $taskGame 'OneConfig') -Recurse}}
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach($library in $taskBase.libraries){
    if(-not(Test-TaskRule $library) -or -not $library.natives.windows){continue}
    $classifier=([string]$library.natives.windows).Replace('${arch}','64')
    $native=Join-Path $taskMinecraft ('libraries/'+$library.downloads.classifiers.PSObject.Properties[$classifier].Value.path)
    $zip=[IO.Compression.ZipFile]::OpenRead($native)
    try{foreach($entry in $zip.Entries){if(-not $entry.Name -or $entry.FullName.StartsWith('META-INF/')){continue};$target=[IO.Path]::GetFullPath((Join-Path (Join-Path $taskGame 'natives') $entry.FullName));if(-not $target.StartsWith([IO.Path]::GetFullPath((Join-Path $taskGame 'natives'))+[IO.Path]::DirectorySeparatorChar)){throw 'Unsafe native path'};New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null;[IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$target,$true)}}finally{$zip.Dispose()}
}
[IO.File]::WriteAllLines((Join-Path $taskGame 'options.txt'),@('fullscreen:false','enableVsync:false','pauseOnLostFocus:false','renderDistance:2','guiScale:2','resourcePacks:[]'),[Text.UTF8Encoding]::new($false))
$taskArgs=@('-Xms512m','-Xmx1024m','-Dfile.encoding=UTF-8','-Dfml.ignoreInvalidMinecraftCertificates=true','-Dfml.ignorePatchDiscrepancies=true',('-Djava.library.path='+ (Join-Path $taskGame 'natives')),('-Dorg.lwjgl.librarypath='+ (Join-Path $taskGame 'natives')),'-cp',($taskClasspath -join ';'),'net.minecraft.launchwrapper.Launch','--username','CodexSmoke','--version','PartyMod-isolated-smoke','--gameDir',$taskGame,'--assetsDir',(Join-Path $taskMinecraft 'assets'),'--assetIndex','1.8','--uuid','00000000000000000000000000000001','--accessToken','0','--userProperties','{}','--userType','legacy','--tweakClass','net.minecraftforge.fml.common.launcher.FMLTweaker','--width','1280','--height','800')
function Quote-TaskArg([string]$value){if($value -notmatch '[\s"]'){return $value};return '"'+(($value -replace '(\\*)"','$1$1\"') -replace '(\\+)$','$1$1')+'"'}
$taskArguments=($taskArgs | ForEach-Object {Quote-TaskArg $_}) -join ' '
Set-Content -LiteralPath (Join-Path $taskProject 'smoke/last-run.txt') -Value $taskGame
if($PrepareOnly){Write-Output $taskGame;exit}
$taskProcess=Start-Process -FilePath $taskJava -ArgumentList $taskArguments -WorkingDirectory $taskGame -WindowStyle Hidden -RedirectStandardOutput (Join-Path $taskGame 'stdout.log') -RedirectStandardError (Join-Path $taskGame 'stderr.log') -PassThru
[pscustomobject]@{Pid=$taskProcess.Id;GameDir=$taskGame}
