package dev.codexcommit.ide;
import com.intellij.openapi.application.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.*;
import git4idea.config.GitVcsApplicationSettings;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
public final class GuiConcurrencyStarter implements ApplicationStarter {
 public boolean isHeadless(){return false;} public int getRequiredModality(){return NOT_IN_EDT;}
 static void waitFor(BooleanSupplier test)throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(!test.getAsBoolean()){if(System.nanoTime()>end)throw new AssertionError("Native asynchronous operation timed out");Thread.sleep(50);}}
 public void main(java.util.List<String> args){try{
  IdeApiRounds.root=Path.of(System.getProperty("codexcommit.qa.root")).toRealPath();var root=IdeApiRounds.root;for(String key:java.util.List.of("idea.config.path","idea.system.path","idea.plugins.path","idea.log.path","user.home"))IdeApiRounds.require(Path.of(System.getProperty(key)).startsWith(root),"Isolation required");
  IdeApiRounds.report=root.resolve("native-results.txt");IdeApiRounds.fixture=Files.createDirectory(root.resolve("synthetic-project"));IdeApiRounds.git("init","--quiet","--template=");Files.writeString(IdeApiRounds.fixture.resolve("source.py"),"print('selected synthetic')\n");
  IdeApiRounds.project=com.intellij.openapi.project.ex.ProjectManagerEx.getInstanceEx().openProject(IdeApiRounds.fixture,com.intellij.ide.impl.OpenProjectTask.build());var project=IdeApiRounds.project;
  IdeApiRounds.edt(()->{GitVcsApplicationSettings.getInstance().setPathToGit(System.getProperty("codexcommit.qa.git"));LocalFileSystem.getInstance().refreshAndFindFileByNioFile(IdeApiRounds.fixture);ProjectLevelVcsManager.getInstance(project).setDirectoryMappings(java.util.List.of(new VcsDirectoryMapping(IdeApiRounds.fixture.toString(),"Git")));});
  waitFor(()->{boolean[] mapped={false};IdeApiRounds.edt(()->{var vcs=ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(IdeApiRounds.file("source.py"));mapped[0]=vcs!=null&&vcs.getVcs()!=null;});return mapped[0];});
  var selection=IdeApiRounds.selection();var service=project.getService(CodexCommitProjectService.class);AtomicInteger calls=new AtomicInteger();AtomicBoolean hold=new AtomicBoolean(true),cancelled=new AtomicBoolean();
  IdeApiRounds.backend(new CodexCommitProjectService.Backend(){public String resolveModel(Path e,BooleanSupplier c){return "gpt-test";}public String generate(Path e,String p,String m,BooleanSupplier c)throws Exception {calls.incrementAndGet();try{while(hold.get()&&!c.getAsBoolean())Thread.sleep(20);}catch(InterruptedException ex){cancelled.set(c.getAsBoolean());throw ex;}cancelled.set(c.getAsBoolean());return "Synthetic generated draft";}});
  JFrame[] frame={null};JButton[] button={null};IdeApiRounds.edt(()->{frame[0]=new JFrame("CodexCommit isolated native concurrency QA");button[0]=new JButton("Generate selected synthetic changes");button[0].addActionListener(e->service.start(selection.ui,selection.message));frame[0].add(button[0]);frame[0].setSize(600,150);frame[0].setLocation(50,50);frame[0].setAlwaysOnTop(true);frame[0].setVisible(true);});
  Robot robot=new Robot();robot.setAutoDelay(70);Runnable click=()->{Point[] point={null};IdeApiRounds.edt(()->point[0]=button[0].getLocationOnScreen());robot.mouseMove(point[0].x+100,point[0].y+30);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);};
  click.run();waitFor(()->calls.get()==1&&service.isBusy());for(int i=0;i<5;i++)click.run();Thread.sleep(300);IdeApiRounds.require(calls.get()==1,"Repeated native clicks created concurrent backend jobs");IdeApiRounds.record("PASS five native mouse clicks while a real background job runs produce one backend call");
  IdeApiRounds.edt(()->WriteAction.run(()->selection.message.setText("Native typed message")));hold.set(false);waitFor(()->!service.isBusy());IdeApiRounds.require(selection.message.getText().equals("Native typed message"),"Typed message replaced");IdeApiRounds.record("PASS typed message survives asynchronous generation completion");
  hold.set(true);click.run();waitFor(()->calls.get()==2&&service.isBusy());IdeApiRounds.edt(service::dispose);waitFor(()->!service.isBusy());IdeApiRounds.require(cancelled.get(),"Backend cancellation was not observed");IdeApiRounds.require(selection.message.getText().equals("Native typed message"),"Disposed result applied");IdeApiRounds.record("PASS native EDT disposal interrupts background work and preserves the message");
  IdeApiRounds.edt(()->{frame[0].dispose();com.intellij.openapi.project.ex.ProjectManagerEx.getInstanceEx().forceCloseProject(project,true);});IdeApiRounds.require(project.isDisposed(),"Project remains open");IdeApiRounds.record("PASS synthetic project closes after background cancellation");IdeApiRounds.record("TOTAL 4 passed, 0 failed; native mouse input on test button, proxy selection, synthetic backend; production Commit toolbar not exercised");Thread.sleep(1500);System.exit(0);
 }catch(Throwable e){e.printStackTrace();System.exit(3);}}
}
