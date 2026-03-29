$base='http://localhost:8000/api'
function Invoke-ApiCheck {
  param($Name,$Method,$Url,$Body=$null,$Accept='application/json')
  try {
    $params=@{Uri="$base$Url";Method=$Method;Headers=@{Accept=$Accept};SkipHttpErrorCheck=$true;TimeoutSec=45}
    if($null -ne $Body){
      $params.ContentType='application/json; charset=utf-8'
      if($Body -is [string]){$params.Body=$Body}else{$params.Body=($Body|ConvertTo-Json -Depth 8)}
    }
    $resp=Invoke-WebRequest @params
    return [pscustomobject]@{name=$Name;status=[int]$resp.StatusCode;body=($resp.Content.ToString().Substring(0,[Math]::Min(180,$resp.Content.ToString().Length)).Replace("`r",' ').Replace("`n",' '))}
  } catch {
    return [pscustomobject]@{name=$Name;status=-1;body=$_.Exception.Message}
  }
}
$res=@()
$res += Invoke-ApiCheck 'schedule-create-T' 'POST' '/schedule' @{title='FixCheck';description='x';eventTime='2026-03-31T09:00:00';location='R2'}
$res += Invoke-ApiCheck 'schedule-stream' 'GET' '/schedule/stream' $null 'text/event-stream'
$session=((Invoke-WebRequest -Uri "$base/chat/history/session?title=FixAudit" -Method POST -SkipHttpErrorCheck).Content|ConvertFrom-Json).data.id
$res += Invoke-ApiCheck 'chat-complete-session' 'GET' "/chat/complete/session?message=hello&sessionId=$session"
$res += Invoke-ApiCheck 'chat-stream-session' 'GET' "/chat/stream/session?message=hello&sessionId=$session" $null 'text/event-stream'
$res += Invoke-ApiCheck 'chat-stream-session-json' 'GET' "/chat/stream/session/json?message=hello&sessionId=$session" $null 'text/event-stream'
$res += Invoke-ApiCheck 'chat-stream' 'GET' '/chat/stream?message=hello' $null 'text/event-stream'
$res += Invoke-ApiCheck 'chat-stream-json' 'GET' '/chat/stream/json?message=hello' $null 'text/event-stream'
$res += Invoke-ApiCheck 'chat-stream-test' 'GET' '/chat/stream/test?message=hello' $null 'text/event-stream'
$res += Invoke-ApiCheck 'search-summary' 'GET' '/search/summary?query=OpenAI' $null 'text/event-stream'
$res += Invoke-ApiCheck 'mcp-agent-stream-memory' 'GET' '/mcp/agent/chat/stream/audit-session?message=hello' $null 'text/event-stream'
$res += Invoke-ApiCheck 'model-test' 'POST' '/model/test' @{name='Temp';provider='openai';apiKey='bad-key';baseUrl='https://api.openai.com/v1';modelName='gpt-4o-mini'}
$kbResp = Invoke-WebRequest -Uri "$base/knowledge" -Method POST -ContentType 'application/json; charset=utf-8' -Body (@{name='FixKB';description='kb';enabled=$true;chunkSize=200;chunkOverlap=20}|ConvertTo-Json) -SkipHttpErrorCheck -TimeoutSec 45
$kbObj = $kbResp.Content | ConvertFrom-Json
$res += [pscustomobject]@{name='knowledge-create';status=[int]$kbResp.StatusCode;body=$kbResp.Content.Substring(0,[Math]::Min(180,$kbResp.Content.Length)).Replace("`r",' ').Replace("`n",' ')}
if($kbObj.data.id){
  $kid=$kbObj.data.id
  $res += Invoke-ApiCheck 'knowledge-search' 'GET' "/knowledge/$kid/search?query=test&topK=3"
  $res += Invoke-ApiCheck 'knowledge-query' 'GET' "/knowledge/$kid/query?question=test&topK=3"
}
$res | ConvertTo-Json -Depth 4
