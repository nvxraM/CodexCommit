package dev.codexcommit.ide;
import com.intellij.openapi.application.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.vcs.commit.*;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.config.GitVcsApplicationSettings;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BooleanSupplier;

public final class IdeApiRounds implements ApplicationStarter {
 static Path root, fixture, report; static Project project; static int passed,failed;
 interface Check {void run() throws Exception;}
 static void edt(Runnable r){if(ApplicationManager.getApplication().isDispatchThread())r.run();else ApplicationManager.getApplication().invokeAndWait(r);}
 static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void record(String s)throws Exception{Files.writeString(report,s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);System.out.println(s);}
 static void test(String name,Check c)throws Exception{try{com.intellij.openapi.progress.ProgressManager.getInstance().runProcess(()->{try{c.run();}catch(Exception e){throw new RuntimeException(e);}},new com.intellij.openapi.progress.EmptyProgressIndicator());passed++;record("PASS "+name);}catch(Throwable e){failed++;record("FAIL "+name+": "+e);e.printStackTrace();}}
 static String git(String...args)throws Exception{List<String> cmd=new ArrayList<>(List.of(System.getProperty("codexcommit.qa.git"),"--no-optional-locks","-c","core.hooksPath=NUL","-c","commit.gpgsign=false"));cmd.addAll(List.of(args));var b=new ProcessBuilder(cmd).directory(fixture.toFile()).redirectErrorStream(true);b.environment().put("GIT_CONFIG_GLOBAL","NUL");b.environment().put("GIT_CONFIG_NOSYSTEM","1");var p=b.start();String text=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);if(p.waitFor()!=0)throw new Exception("Synthetic Git command failed");return text.strip();}
 static FilePath file(String n){return VcsUtil.getFilePath(fixture.resolve(n).toFile(),false);}
 static final class Selection {
  List<Change> changes=new ArrayList<>();List<FilePath> unversioned=new ArrayList<>();Document message;CommitWorkflowUi ui;
  Selection(){edt(()->message=EditorFactory.getInstance().createDocument("User message"));CommitMessageUi commit=new CommitMessageUi(){public String getText(){return message.getText();}public void setText(String s){WriteAction.run(()->message.setText(s));}public void focus(){}public void startLoading(){}public void stopLoading(){}};ui=(CommitWorkflowUi)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{CommitWorkflowUi.class},(p,m,a)->switch(m.getName()){case "getIncludedChanges","getDisplayedChanges"->List.copyOf(changes);case "getIncludedUnversionedFiles","getDisplayedUnversionedFiles"->List.copyOf(unversioned);case "getCommitMessageUi"->commit;case "activate"->true;default->null;});}
  ChangeSnapshot capture()throws Exception{ChangeSnapshot[] s={null};Exception[] e={null};edt(()->{try{s[0]=ChangeSnapshot.capture(project,ui,message);}catch(Exception x){e[0]=x;}});if(e[0]!=null)throw e[0];return s[0];}
 }
 static Selection selection(){var s=new Selection();s.unversioned.add(file("source.py"));return s;}
 static void refuse(Check c)throws Exception{try{c.run();throw new AssertionError("Unsafe selection accepted");}catch(java.io.IOException|dev.codexcommit.core.ContentPolicy.ValidationException expected){}}
 static void backend(CodexCommitProjectService.Backend backend)throws Exception{Path exe=root.resolve("mock-native/codex.exe");Files.createDirectories(exe.getParent());Files.write(exe,new byte[]{'M','Z',0,0});CodexCommitSettings.getInstance().setExecutable(exe.toString());var service=project.getService(CodexCommitProjectService.class);require(!service.isBusy(),"Previous job busy");var f=CodexCommitProjectService.class.getDeclaredField("backend");f.setAccessible(true);f.set(service,backend);}
 static void start(Selection s){edt(()->project.getService(CodexCommitProjectService.class).start(s.ui,s.message));require(!project.getService(CodexCommitProjectService.class).isBusy(),"Headless task did not complete");}
 @Override public boolean isHeadless(){return true;}@Override public int getRequiredModality(){return NOT_IN_EDT;}
 @Override public void main(List<String> args){try{
  int round=Integer.getInteger("codexcommit.qa.round",1);root=Path.of(System.getProperty("codexcommit.qa.root")).toRealPath();for(String key:List.of("idea.config.path","idea.system.path","idea.plugins.path","idea.log.path","user.home")){require(Path.of(System.getProperty(key)).toAbsolutePath().normalize().startsWith(root),"QA requires isolated directories under its root");}require(Path.of(System.getProperty("codexcommit.qa.git")).isAbsolute(),"QA requires an absolute native Git path");report=root.resolve("result-round-"+round+".txt");Files.writeString(report,"");fixture=Files.createDirectory(root.resolve("round-"+round+"-"+System.currentTimeMillis()));git("init","--quiet","--template=");Files.writeString(fixture.resolve("baseline.py"),"print('baseline')\n");git("add",".");git("-c","user.name=Synthetic QA","-c","user.email=qa@example.invalid","commit","--quiet","-m","Create synthetic baseline");Files.writeString(fixture.resolve("source.py"),"print('selected')\n");project=com.intellij.openapi.project.ex.ProjectManagerEx.getInstanceEx().openProject(fixture,com.intellij.ide.impl.OpenProjectTask.build());edt(()->{GitVcsApplicationSettings.getInstance().setPathToGit(System.getProperty("codexcommit.qa.git"));LocalFileSystem.getInstance().refreshAndFindFileByNioFile(fixture);ProjectLevelVcsManager.getInstance(project).setDirectoryMappings(List.of(new VcsDirectoryMapping(fixture.toString(),"Git")));});
  record("ROUND "+round+" actual PyCharm API; synthetic backend; no native GUI input");
  test("regression selected addition and unchanged index",()->{String tree=git("write-tree");require(selection().capture().build(()->false).contains("source.py"),"Missing selected path");require(tree.equals(git("write-tree")),"Index altered");});
  test("regression selection freshness",()->{var s=selection();var snapshot=s.capture();snapshot.build(()->false);edt(()->{require(snapshot.isFresh(),"Initial selection stale");s.unversioned.clear();require(!snapshot.isFresh(),"Removed selection retained");});});
  test("regression binary refusal",()->{Files.write(fixture.resolve("binary.txt"),new byte[]{1,0,2});var s=new Selection();s.unversioned.add(file("binary.txt"));refuse(()->s.capture().build(()->false));});
  test("regression empty gitkeep",()->{Files.writeString(fixture.resolve(".gitkeep"),"");var s=new Selection();s.unversioned.add(file(".gitkeep"));require(s.capture().build(()->false).contains(".gitkeep"),"Empty addition omitted");});
  if(round==1)for(String name:List.of("space name.py","unicode-\u03bb.py","bom8.py","bom16le.py","bom16be.py"))test("encoding and path "+name,()->{String body="print('encoding')\n";byte[] bytes=name.equals("bom16le.py")?("\ufeff"+body).getBytes(StandardCharsets.UTF_16LE):name.equals("bom16be.py")?("\ufeff"+body).getBytes(StandardCharsets.UTF_16BE):((name.equals("bom8.py")?"\ufeff":"")+body).getBytes(StandardCharsets.UTF_8);Files.write(fixture.resolve(name),bytes);var s=new Selection();s.unversioned.add(file(name));String patch=s.capture().build(()->false);require(patch.contains("encoding")&&!patch.contains("\ufeff"),"Encoding mismatch");});
  if(round==2){
   test("outside project refused",()->{var s=new Selection();s.unversioned.add(VcsUtil.getFilePath(root.resolve("outside.py").toFile(),false));refuse(()->s.capture().build(()->false));});
   test("missing file refused",()->{var s=new Selection();s.unversioned.add(file("missing.py"));refuse(()->s.capture().build(()->false));});
   test("unversioned workspace refused",()->{Files.createDirectories(fixture.resolve(".idea"));Files.writeString(fixture.resolve(".idea/workspace.xml"),"<synthetic/>");var s=new Selection();s.unversioned.add(file(".idea/workspace.xml"));refuse(()->s.capture().build(()->false));});
   test("repository private exclude respected",()->{Files.createDirectories(fixture.resolve(".git/info"));Files.writeString(fixture.resolve(".git/info/exclude"),"private.py\n");Files.writeString(fixture.resolve("private.py"),"print('excluded')\n");var s=new Selection();s.unversioned.add(file("private.py"));refuse(()->s.capture().build(()->false));});
   test("duplicate refused",()->{var s=selection();s.unversioned.add(file("source.py"));refuse(()->s.capture().build(()->false));});
  }
  if(round==3)for(int i=0;i<5;i++){final int kind=i;test("provider fault "+kind,()->{var calls=new java.util.concurrent.atomic.AtomicInteger();backend(new CodexCommitProjectService.Backend(){public String resolveModel(Path e,BooleanSupplier c)throws Exception{if(kind==0)throw new java.io.IOException("Synthetic discovery failure");if(kind==1)throw new InterruptedException("Synthetic discovery cancellation");return "gpt-test";}public String generate(Path e,String p,String m,BooleanSupplier c)throws Exception{calls.incrementAndGet();if(kind==2)throw new InterruptedException("Synthetic cancellation");if(kind==3)throw new java.util.concurrent.TimeoutException("Synthetic timeout");throw new java.io.IOException("Synthetic protocol failure");}});var s=selection();start(s);require(s.message.getText().equals("User message"),"Message replaced");require(calls.get()==(kind<2?0:1),"Unexpected request count");});}
  if(round==4)for(int i=0;i<5;i++)test("model discovery reentrancy and stale selection "+i,()->{var resolves=new java.util.concurrent.atomic.AtomicInteger();var generations=new java.util.concurrent.atomic.AtomicInteger();var s=selection();var service=project.getService(CodexCommitProjectService.class);backend(new CodexCommitProjectService.Backend(){public String resolveModel(Path e,BooleanSupplier c){resolves.incrementAndGet();edt(()->{service.start(s.ui,s.message);s.unversioned.clear();});return "gpt-test";}public String generate(Path e,String p,String m,BooleanSupplier c){generations.incrementAndGet();return "Must not apply";}});start(s);require(resolves.get()==1&&generations.get()==0,"Stale request generated");require(s.message.getText().equals("User message"),"Message replaced");});
  if(round==5)for(int i=0;i<10;i++){final int n=i;test("repeated disk freshness "+i,()->{String name="stability-"+n+".py";Files.writeString(fixture.resolve(name),"print('first')\n");var s=new Selection();s.unversioned.add(file(name));var snapshot=s.capture();snapshot.build(()->false);require(snapshot.contentIsFresh(()->false),"Fresh rejected");Files.writeString(fixture.resolve(name),"print('second')\n");require(!snapshot.contentIsFresh(()->false),"Stale accepted");});}
  edt(()->com.intellij.openapi.project.ex.ProjectManagerEx.getInstanceEx().forceCloseProject(project,true));record("TOTAL "+passed+" passed, "+failed+" failed");Thread.sleep(1500);System.exit(failed==0?0:2);
 }catch(Throwable e){e.printStackTrace();System.exit(3);}}
}
