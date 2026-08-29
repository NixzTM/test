import sys,socket,struct,xmlrpc.client,re,json,base64,subprocess,time,hashlib,hmac,urllib.request

def dec(i):
    try:return base64.b64decode(i).decode('utf-8')
    except:return ''
def cfg(server):
    p='/opt/'+server.lower()+'/config/ac.yaml';t=open(p,encoding='utf-8').read()
    block=t.split('dedicated:',1)[1].split('\nmysql:',1)[0]
    port=int(re.search(r'(?m)^\s*port:\s*(\d+)',block).group(1)); login=re.search(r'(?m)^\s*login:\s*["\']?([^"\'\n]+)',block).group(1).strip(); pw=re.search(r'(?m)^\s*password:\s*["\']?([^"\'\n]+)',block).group(1).strip();return port,login,pw
def dbname(server):
    p='/opt/'+server.lower()+'/config/ac.yaml';t=open(p,encoding='utf-8').read();m=re.search(r'(?m)^\s*dsn:\s*["\']?[^/]+/([^?"\']+)',t);return m.group(1) if m else 'ac3'
class G:
 def __init__(self,port,user,pw):
  self.s=socket.create_connection(('127.0.0.1',port),4);n=struct.unpack('<I',self.s.recv(4))[0];self.s.recv(n);self.h=1;self.call('Authenticate',[user,pw])
 def rx(self,n):
  b=b''
  while len(b)<n:
   c=self.s.recv(n-len(b))
   if not c:raise RuntimeError('RPC closed')
   b+=c
  return b
 def call(self,m,p=[]):
  x=xmlrpc.client.dumps(tuple(p),methodname=m,allow_none=True).encode();h=self.h;self.h+=1;self.s.sendall(struct.pack('<II',len(x)+4,h)+x)
  while 1:
   size=struct.unpack('<I',self.rx(4))[0];handle=struct.unpack('<I',self.rx(4))[0];payload=self.rx(size-4)
   if handle==h:return xmlrpc.client.loads(payload)[0][0]
 def close(self): self.s.close()
def rpc(server,method,params):
 p,u,w=cfg(server);g=G(p,u,w)
 try:return g.call(method,params)
 finally:g.close()
def mysql(sql):
 p=subprocess.run(['mariadb','-NBe',sql],text=True,capture_output=True,timeout=5)
 if p.returncode:raise RuntimeError(p.stderr.strip())
 return p.stdout

def main():
 action=sys.argv[1];server=sys.argv[2];args=[dec(x) for x in sys.argv[3:]]
 if action=='state':
  m=rpc(server,'GetCurrentChallengeInfo',[]);players=rpc(server,'GetPlayerList',[200,0]);print(json.dumps({'map':m,'players':players},ensure_ascii=False));return
 if action=='pm': rpc(server,'ChatSendServerMessageToLogin',[args[1],args[0]]);print('{}');return
 if action=='warn': rpc(server,'ChatSendServerMessageToLogin',['$f00Warning: $fff'+args[1],args[0]]);print('{}');return
 if action=='kick': rpc(server,'Kick',[args[0],args[1]]);print('{}');return
 if action=='announce': rpc(server,'ChatSendServerMessage',[args[1]]);print('{}');return
 if action=='restart': rpc(server,'RestartChallenge',[]);print('{}');return
 if action=='next': rpc(server,'NextChallenge',[]);print('{}');return
 if action=='timer': rpc(server,'SetTimeAttackLimit',[int(args[0])*1000]);print('{}');return
 if action=='mute':
  db=dbname(server);login=args[0].replace("'","''");mins=int(args[1] or 0);reason=args[2].replace("'","''");until='NULL' if mins<=0 else "DATE_ADD(NOW(), INTERVAL %d MINUTE)"%mins
  mysql("INSERT INTO `%s`.ac_mutes (Login,MutedUntil,Reason,ByLogin,UpdatedAt) VALUES ('%s',%s,'%s','mobile',NOW()) ON DUPLICATE KEY UPDATE MutedUntil=VALUES(MutedUntil),Reason=VALUES(Reason),ByLogin=VALUES(ByLogin),UpdatedAt=NOW()"%(db,login,until,reason));print('{}');return
 if action=='unmute':
  db=dbname(server);login=args[0].replace("'","''");mysql("DELETE FROM `%s`.ac_mutes WHERE Login='%s'"%(db,login));print('{}');return
 if action=='search':
  db=dbname(server);q=args[0].replace("'","''");sql="SELECT TABLE_SCHEMA,TABLE_NAME FROM information_schema.COLUMNS WHERE COLUMN_NAME='Login' AND TABLE_SCHEMA='%s' GROUP BY TABLE_SCHEMA,TABLE_NAME HAVING SUM(COLUMN_NAME IN ('NickName','Nickname','Nick'))>0 LIMIT 1"%db;tab=mysql(sql).strip().split('\t');out=[]
  if len(tab)==2:
   cols=mysql("SHOW COLUMNS FROM `%s`.`%s`"%(tab[0],tab[1]));nick='NickName' if '\tNickName\t' in '\t'+cols.replace('\n','\t') else ('Nickname' if 'Nickname' in cols else 'Nick')
   rows=mysql("SELECT HEX(Login),HEX(COALESCE(`%s`,Login)) FROM `%s`.`%s` WHERE Login LIKE '%%%s%%' OR `%s` LIKE '%%%s%%' LIMIT 50"%(nick,tab[0],tab[1],q,nick,q))
   for line in rows.splitlines():
    a=line.split('\t')
    if len(a)>=2: out.append({'login':bytes.fromhex(a[0]).decode('utf-8','replace'),'nick':bytes.fromhex(a[1]).decode('utf-8','replace')})
  print(json.dumps(out,ensure_ascii=False));return
 if action=='chat':
  secret=open('/etc/arena-crosschat.token','rb').read().strip();path='/v1/messages?after='+str(max(0,int(args[0] or 0)));ts=str(int(time.time()));nonce=hashlib.sha256((ts+server+str(time.time_ns())).encode()).hexdigest()[:32];body=b'';canon=(ts+'\nGET\n'+path+'\n'+hashlib.sha256(body).hexdigest()).encode();sig=hmac.new(secret,canon,hashlib.sha256).hexdigest();r=urllib.request.Request('http://127.0.0.1:47831'+path,headers={'X-Arena-Timestamp':ts,'X-Arena-Nonce':nonce,'X-Arena-Signature':sig});print(urllib.request.urlopen(r,timeout=4).read().decode());return
 raise RuntimeError('Unknown action')
try:main()
except Exception as e: print('ERR:'+str(e))
