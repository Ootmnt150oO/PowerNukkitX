package cn.nukkit.event.player;

import cn.nukkit.Player;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;

public class PlayerGameModeChangeEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    protected byte gamemode;

    public PlayerGameModeChangeEvent(Player player, byte newGameMode) {
        this.player = player;
        this.gamemode = newGameMode;
    }

    public byte getNewGamemode() {
        return gamemode;
    }
}
