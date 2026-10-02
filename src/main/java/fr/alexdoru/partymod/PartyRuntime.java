package fr.alexdoru.partymod;

import fr.alexdoru.partymod.core.*;
import fr.alexdoru.partymod.data.*;
import fr.alexdoru.partymod.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.*;
import net.minecraft.scoreboard.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import java.io.File;
import java.util.*;
import java.util.regex.*;

public final class PartyRuntime implements ActionQueue.Transport {
    public final PartyState state=new PartyState();
    public final LocalStore store;
    public final Notifications notifications=new Notifications();
    public final ActionQueue actions=new ActionQueue();
    private final SyncPolicy syncPolicy=new SyncPolicy();
    private final DataClient data=new DataClient();
    private boolean wasEnabled,wasActive;private long nextRules,nextSight;private boolean match;
    private String syncLeader="",lastStoreError="";
    private final Map<String,Long> handledKicks=new HashMap<>();
    public PartyRuntime(File records){store=new LocalStore(records);}
    public static void chat(String text){Minecraft mc=Minecraft.getMinecraft();if(mc.thePlayer!=null)mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.LIGHT_PURPLE+"[PartyMod] "+EnumChatFormatting.GRAY+text));}
    public boolean active(){Minecraft mc=Minecraft.getMinecraft();return PartyMod.config!=null&&PartyMod.config.enabled&&mc.thePlayer!=null&&mc.theWorld!=null&&mc.getCurrentServerData()!=null&&PartyMessages.hypixel(mc.getCurrentServerData().serverIP);}
    public boolean rosterFresh(){return state.fresh(System.currentTimeMillis(),(long)PartyMod.config.freshness);}
    public boolean inMatch(){return match;}
    public String self(){return Minecraft.getMinecraft().thePlayer==null?"":Minecraft.getMinecraft().thePlayer.getName();}
    public void applyEnabled(){if(PartyMod.config==null)return;if(wasEnabled!=PartyMod.config.enabled){wasEnabled=PartyMod.config.enabled;cancel();if(wasEnabled)syncPolicy.restartActivation();notify("enabled",wasEnabled?"Organiser enabled — sync the current party":"Organiser disabled; pending work cancelled",true);}}
    private void cancel(){state.invalidateWork();data.cancel();actions.cancel(this);handledKicks.clear();}
    private void reset(){cancel();state.reset();syncPolicy.partyEnded();match=false;notifications.clear();store.flush(true);}
    private void resetConnection(){reset();wasActive=false;syncPolicy.restartActivation();}
    @SubscribeEvent public void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event){Minecraft.getMinecraft().addScheduledTask(this::resetConnection);}
    @SubscribeEvent public void connect(FMLNetworkEvent.ClientConnectedToServerEvent event){Minecraft.getMinecraft().addScheduledTask(this::resetConnection);}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){if(event.phase!=TickEvent.Phase.END||PartyMod.config==null)return;applyEnabled();boolean active=active();if(wasActive&&!active)cancel();if(syncPolicy.activation(active))sync();wasActive=active;store.flush(false);if(!active)return;long now=System.currentTimeMillis();
        boolean changed=configureData();if(changed)assessAll();else for(Member member:state.members())if(member.status==Member.Status.CANCELLED)assess(member);
        actions.tick(now,(long)PartyMod.config.commandSpacing,this);
        if(state.syncStartedAt>0&&now-state.syncStartedAt>10000){state.syncFailed();notify("sync-failed","Roster synchronisation incomplete — retry",true);}
        if(now>=nextRules){nextRules=now+1000;match=detectMatch();reassess();checkNewAccounts();if(store.loadError.isEmpty())lastStoreError="";if(!store.loadError.isEmpty()&&!store.loadError.equals(lastStoreError)){lastStoreError=store.loadError;notify("local-store:"+store.loadError,store.loadError,true);}for(LocalStore.WaitEntry entry:store.waitlist)if(entry.status.equals("Invitation sent")&&now-entry.invitedAt>60000){entry.status="Invitation unconfirmed";store.touch();}}
        if(match&&now>=nextSight){nextSight=now+30000;for(Object o:Minecraft.getMinecraft().theWorld.playerEntities){EntityPlayer player=(EntityPlayer)o;Member m=state.get(player.getName());if(m!=null&&!player.isSpectator()){store.observeMwSighting(m,now);}}}
    }
    private boolean detectMatch(){Minecraft mc=Minecraft.getMinecraft();if(mc.theWorld==null)return false;Scoreboard board=mc.theWorld.getScoreboard();ScoreObjective objective=board.getObjectiveInDisplaySlot(1);if(objective==null||!EnumChatFormatting.getTextWithoutFormattingCodes(objective.getDisplayName()).toUpperCase(Locale.ROOT).contains("MEGA WALLS"))return false;for(Score score:board.getSortedScores(objective)){String line=EnumChatFormatting.getTextWithoutFormattingCodes(ScorePlayerTeam.formatPlayerName(board.getPlayersTeam(score.getPlayerName()),score.getPlayerName())).toLowerCase(Locale.ROOT);if(line.contains("wither")||line.contains("deathmatch"))return true;}return false;}
    @SubscribeEvent public void onChat(ClientChatReceivedEvent event){if(event.type!=0||!active())return;String text=EnumChatFormatting.getTextWithoutFormattingCodes(event.message.getUnformattedText()).trim();PartyMessages.Event parsed=PartyMessages.parse(text);long now=System.currentTimeMillis();try{switch(parsed.type){
        case SELF_JOIN:reset();syncPolicy.partyJoined();state.join(self(),now);state.role(parsed.name,Member.Role.LEADER,now);sync();break;
        case JOIN:{boolean newParty=state.members().isEmpty();Member existing=state.get(parsed.name);Member m=state.join(parsed.name,now);if(existing==null){if(newParty){syncPolicy.partyJoined();sync();}m.ageCandidate=true;store.observePartyJoin(m,now);assess(m);notify("join:"+m.key,"Joined: "+m.name,false);}for(LocalStore.WaitEntry entry:store.waitlist)if(entry.name.equalsIgnoreCase(m.name)){entry.status="Joined party";store.touch();}break;}
        case LEFT:if(parsed.name.equalsIgnoreCase(self())){reset();notify("party-ended","You left the party; pending actions cancelled",true);break;}state.leave(parsed.name);notify("leave:"+parsed.name,parsed.name+" left the party",false);break;
        case KICK:confirmedKick(parsed.name,now);break;
        case HEADER:syncLeader=state.members().stream().filter(m->m.role==Member.Role.LEADER).map(m->m.key).findFirst().orElse("");state.beginSync(parsed.count,now);break;
        case ROLE:if(state.staging()){for(String name:PartyMessages.names(parsed.text))state.stage(name,parsed.role);if(state.commitSync(now)){String newLeader=state.members().stream().filter(m->m.role==Member.Role.LEADER).map(m->m.key).findFirst().orElse("");actions.acknowledgeSync(this);syncPolicy.synced();if(!syncLeader.isEmpty()&&!syncLeader.equals(newLeader)){cancel();state.synced=true;state.rosterAt=now;}assessAll();notify("sync","Roster synchronised: "+state.members().size()+" members",true);}}break;
        case PROMOTE:state.role(parsed.name,parsed.role,now);actions.cancel(this);notify("role",parsed.name+" is now party "+parsed.role.name().toLowerCase(Locale.ROOT),true);break;
        case DISBAND:case NO_PARTY:reset();notify("party-ended","Party ended; pending actions cancelled",true);break;
        case CHAT:if(state.acknowledge(parsed.name,parsed.text))notify("ready:"+state.round+":"+parsed.name,parsed.name+" acknowledged readiness",false);break;
        case DISCONNECT:case RECONNECT:{Member m=state.get(parsed.name);if(m!=null)m.disconnectedAt=parsed.type==PartyMessages.Type.DISCONNECT?now:0;break;}
        default:break;
    }}catch(IllegalArgumentException|IllegalStateException ex){state.syncFailed();notify("parse","Party update could not be reconciled — sync roster",true);}
        acknowledgeActions(text);
    }
    private void confirmedKick(String name,long now){if(name.equalsIgnoreCase(self())){reset();notify("party-ended","You were removed; pending actions cancelled",true);return;}Member removed=state.leave(name);actions.acknowledge(ActionQueue.Kind.REMOVE,name,"Removal confirmed",this);actions.acknowledge(ActionQueue.Kind.NEW_ACCOUNT_REMOVE,name,"Removal confirmed",this);String key=state.generation+":"+Member.key(name);Long previous=handledKicks.put(key,now);if(previous!=null&&now-previous<120000)return;handledKicks.entrySet().removeIf(e->now-e.getValue()>120000);notify("kick:"+key,name+" was kicked from the party",true);
        if(PartyMod.config.autoBlock){ActionQueue.Action action=new ActionQueue.Action(ActionQueue.Kind.BLOCK,name,"/block add "+name,state.generation,0,now);action.automatic=true;actions.enqueue(action);}
    }
    private void acknowledgeActions(String text){if(text.equals("Woah slow down, you're doing that too fast!")||text.equals("You are sending commands too fast! Please slow down.")){actions.cooldown(System.currentTimeMillis()+10000);ActionQueue.Action refused=actions.pending();if(refused!=null)actions.acknowledge(refused.kind,refused.name,"Server command throttle — paused for 10 seconds",this);notify("command-throttle","Hypixel command throttle — queued commands paused for 10 seconds",true);return;}if(text.matches("^You (?:are|have|cannot).{0,200}competitive ban.{0,200}$")||text.equals("You cannot join this game right now!"))notify("queue-refusal","Server restriction/refusal observed; attribution unverified: "+text,true);ActionQueue.Action pending=actions.pending();if(pending==null)return;String escaped=Pattern.quote(pending.name);switch(pending.kind){
        case BLOCK:if(text.matches("^(?:Added "+escaped+" to your blocked players list\\.|You (?:have )?blocked "+escaped+"[!.]|"+escaped+" is already (?:on your blocked players list|blocked)[!.])$"))actions.acknowledge(pending.kind,pending.name,"Block confirmed",this);break;
        case IGNORE:if(text.matches("^(?:Added "+escaped+" to your ignore list\\.|You are now ignoring "+escaped+"[!.]|You are already ignoring "+escaped+"[!.])$"))actions.acknowledge(pending.kind,pending.name,"Ignore confirmed",this);break;
        case INVITE:if(text.matches("^You (?:have )?invited (?:\\[[^\\]]+\\] )?"+escaped+" to (?:your|the) party[!.]$"))actions.acknowledge(pending.kind,pending.name,"Invitation sent",this);break;
        default:break;
    }
        if(text.equals("You do not have permission to execute this command!")||text.equals("You are sending commands too fast! Please slow down.")||text.equals("You cannot remove the party leader!")||text.equals("You cannot use this command while in a game!"))actions.acknowledge(pending.kind,pending.name,"Server refused: "+text,this);
    }
    public void notify(String key,String text,boolean operational){notifications.add(key,text,operational);}
    public void sync(){if(!active()){chat("Join Hypixel and enable the organiser to sync.");return;}if(actions.contains(ActionQueue.Kind.SYNC,""))return;queue(ActionQueue.Kind.SYNC,"","/p list",0);}
    private boolean queue(ActionQueue.Kind kind,String name,String command,long membership){boolean added=actions.enqueue(new ActionQueue.Action(kind,name,command,state.generation,membership,System.currentTimeMillis()));if(!added)notify("queue-rejected","Action already queued, recently handled, or queue full",true);return added;}
    public void remove(Member member){if(!active()||!rosterFresh()||!state.canRemove(self(),member)){chat("Removal needs a fresh roster and party removal authority.");return;}queue(ActionQueue.Kind.REMOVE,member.name,"/p remove "+member.name,member.membershipId);}
    public void block(Member member){if(active()&&state.get(member.name)==member)queue(ActionQueue.Kind.BLOCK,member.name,"/block add "+member.name,member.membershipId);}
    public void ignore(Member member){if(active()&&state.get(member.name)==member)queue(ActionQueue.Kind.IGNORE,member.name,"/ignore add "+member.name,member.membershipId);}
    public void invite(LocalStore.WaitEntry entry){Member actor=state.get(self());if(!active()||!rosterFresh()||actor==null||actor.role!=Member.Role.LEADER&&actor.role!=Member.Role.MODERATOR){chat("Invitation needs a fresh roster and host role.");return;}if(!store.waitlist.contains(entry)||actions.contains(ActionQueue.Kind.INVITE,entry.name)||entry.status.equals("Invitation sent"))return;if(state.members().size()>=(int)PartyMod.config.capacity){chat("Configured event capacity reached.");return;}ActionQueue.Action invitation=new ActionQueue.Action(ActionQueue.Kind.INVITE,entry.name,"/p invite "+entry.name,state.generation,0,System.currentTimeMillis());invitation.owner=entry;if(actions.enqueue(invitation)){entry.status="Queued";store.touch();}}
    public void startReady(){Member actor=state.get(self());if(!active()||!rosterFresh()||actor==null||actor.role!=Member.Role.LEADER){chat("Readiness needs a fresh roster and party leader.");return;}if(actions.contains(ActionQueue.Kind.READY,"")||actions.size()>=128)return;String round=state.startReadyRound();actor.ready=true;ActionQueue.Action ready=new ActionQueue.Action(ActionQueue.Kind.READY,"","/pc Ready check "+round+" - reply: ready "+round,state.generation,0,System.currentTimeMillis());ready.context=round;if(!actions.enqueue(ready)){state.round="";return;}notify("round:"+round,"Readiness round started: "+round,true);}
    private boolean configureData(){boolean changed=data.configureSource(PartyMod.config.statsSource==0,PartyMod.config.hypixelApiKey,PartyMod.config.serviceUrl,PartyMod.config.serviceToken,(int)PartyMod.config.cacheMinutes,(int)PartyMod.config.requestBudget);if(changed)for(Member m:state.members()){m.assessmentId++;m.stats=null;m.status=Member.Status.CANCELLED;}return changed;}
    public void assessAll(){if(!active())return;configureData();for(Member m:state.members())if(m.status!=Member.Status.PENDING)assess(m);}
    private boolean tooNew(Member m){return NewAccountPolicy.eligible(m,PartyMod.config.autoKickNewAccounts,(int)PartyMod.config.minimumAccountDays,System.currentTimeMillis());}
    private void checkNewAccounts(){for(Member m:state.members()){if(m.ageKickAttempted||!tooNew(m)||actions.contains(ActionQueue.Kind.NEW_ACCOUNT_REMOVE,m.name)||actions.contains(ActionQueue.Kind.REMOVE,m.name))continue;Member actor=state.get(self());if(actor!=null&&actor.role==Member.Role.MEMBER)continue;if(!rosterFresh()){if(!actions.contains(ActionQueue.Kind.SYNC,"")&&syncPolicy.recovery())sync();continue;}if(!state.canRemove(self(),m))continue;if(queue(ActionQueue.Kind.NEW_ACCOUNT_REMOVE,m.name,"/p remove "+m.name,m.membershipId)){notify("new-account:"+m.key,m.name+" queued for removal: Hypixel first login is below the configured age",true);}}}
    private void assess(Member m){long generation=state.generation,id=m.membershipId,assessment=++m.assessmentId;m.status=Member.Status.PENDING;data.request(m.name,result->Minecraft.getMinecraft().addScheduledTask(()->{if(!active()||!state.valid(generation,m.name,id)||m.assessmentId!=assessment)return;m.error=result.error;if(result.stats!=null){m.acceptStats(result.stats,result.cached,System.currentTimeMillis());}else{m.stats=null;m.status=result.error.equals("Cancelled")?Member.Status.CANCELLED:result.error.equals("No data service configured")?Member.Status.NOT_ASSESSED:result.error.contains("configuration")?Member.Status.FAILED:Member.Status.UNAVAILABLE;}reassess(m);}));}
    public void reassess(){if(PartyMod.config==null)return;for(Member member:state.members())reassess(member);}
    private void reassess(Member m){String previous=m.fingerprint();LocalStore.Record record=store.record(m);long now=System.currentTimeMillis();List<String> dataReasons=ReviewRules.evaluate(m.stats,PartyMod.config.rules(),now,null),localReasons=ReviewRules.evaluate(null,PartyMod.config.rules(),now,record.lastMwSighting==0?null:record.lastMwSighting);if(record.blocked)localReasons.add("On your local host blocklist");m.updateReview(dataReasons,localReasons,m.stats!=null);if(m.needsReview()&&!previous.equals(m.fingerprint()))notify("review:"+m.key+":"+m.fingerprint(),m.name+" needs review — "+m.reasons.size()+" reasons",false);}
    public List<String> precheck(){List<String> lines=new ArrayList<>();long absent=state.members().stream().filter(m->m.status==Member.Status.UNAVAILABLE||m.status==Member.Status.FAILED||m.status==Member.Status.NOT_ASSESSED||m.status==Member.Status.CANCELLED).count();Member actor=state.get(self());lines.add("PRE-QUEUE REVIEW");lines.add("Roster: "+(rosterFresh()?"current":"synchronisation required"));lines.add("Party leader: "+(actor!=null&&actor.role==Member.Role.LEADER?"confirmed":"not confirmed"));lines.add("Members: "+state.members().size()+" / "+(int)PartyMod.config.capacity);lines.add("Host reviews: "+state.countReview()+"; pending data: "+state.countPending()+"; unavailable/not assessed: "+absent);lines.add("Readiness: "+(state.round.isEmpty()?"not started":state.members().stream().filter(m->!m.ready).count()+" acknowledgements outstanding"));lines.add("Disconnected/grace: "+state.members().stream().filter(m->m.disconnectedAt>0).count());lines.add("Advisory checks only. Server acceptance and ban status are unknown.");return lines;}
    @Override public boolean valid(ActionQueue.Action a){return ActionPolicy.valid(a,state,self(),active(),rosterFresh(),PartyMod.config.autoBlock,PartyMod.config.autoKickNewAccounts,(int)PartyMod.config.minimumAccountDays,(int)PartyMod.config.capacity,System.currentTimeMillis(),a.owner instanceof LocalStore.WaitEntry&&store.waitlist.contains(a.owner));}
    @Override public void send(String command){if(command.startsWith("/p remove ")){Member member=state.get(command.substring(10));if(member!=null)member.ageKickAttempted=true;}if(command.equals("/p list")){state.synced=false;state.syncStartedAt=System.currentTimeMillis();}Minecraft.getMinecraft().thePlayer.sendChatMessage(command);}
    @Override public void result(ActionQueue.Action a,String result){a.result=result;notify("action:"+a.kind+":"+a.name+":"+a.queuedAt,a.kind+" "+a.name+": "+result,true);if(a.kind==ActionQueue.Kind.READY)state.readyResult(a.context,result.startsWith("Sent"));if(a.kind==ActionQueue.Kind.INVITE)for(LocalStore.WaitEntry e:store.waitlist)if(e==a.owner){e.status=result;e.invitedAt=System.currentTimeMillis();store.touch();}}
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Post event){if(event.type!=RenderGameOverlayEvent.ElementType.ALL||!active()||!PartyMod.config.notifications)return;Notifications.Entry toast=notifications.toast();if(toast==null||match&&PartyMod.config.quietMatch&&!toast.operational)return;int width=event.resolution.getScaledWidth();fr.alexdoru.partymod.ui.PartyScreen.drawToast(toast,width);}
    public void shutdown(){data.shutdown();store.flush(true);}
}
