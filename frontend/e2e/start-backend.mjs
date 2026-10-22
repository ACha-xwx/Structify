import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const root=fileURLToPath(new URL('../../backend/spring/',import.meta.url));
const options=['-B','spring-boot:test-run',
  '-Dspring-boot.run.main-class=com.feng.dsagent.studyroom.harness.StudyroomHarnessApplication',
  '-Dspring-boot.run.profiles=test,harness'];
const windows=process.platform==='win32';
const child=spawn(windows?'cmd.exe':'./mvnw',windows?['/d','/s','/c','mvnw.cmd '+options.join(' ')]:options,
  {cwd:root,stdio:'inherit',windowsHide:true,env:{...process.env,SERVER_PORT:'8893'}});
child.on('error',error=>{process.stderr.write(error.message+'\n');process.exitCode=1;});
child.on('exit',code=>{process.exitCode=code??1;});
