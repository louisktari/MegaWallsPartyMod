package fr.alexdoru.partymod.core;

/** Party replies do not start a new connection or retry a failed automatic sync. */
public final class SyncPolicy {
    private boolean activationUsed,recoveryUsed;
    public void restartActivation(){activationUsed=false;recoveryUsed=false;}
    public boolean activation(boolean active){if(!active||activationUsed)return false;activationUsed=true;return true;}
    public boolean recovery(){if(recoveryUsed)return false;recoveryUsed=true;return true;}
    public void partyEnded(){recoveryUsed=true;}
    public void partyJoined(){activationUsed=true;recoveryUsed=false;}
    public void synced(){recoveryUsed=false;}
}
