package cn.nukkit.command.defaults;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandParser;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.command.exceptions.CommandSyntaxException;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.TextFormat;

import java.text.DecimalFormat;

/**
 * @author xtypr
 * @since 2015/12/13
 */
public class SetWorldSpawnCommand extends VanillaCommand {
    public SetWorldSpawnCommand(String name) {
        super(name, "commands.setworldspawn.description");
        this.setPermission("nukkit.command.setworldspawn");
        this.commandParameters.clear();
        this.commandParameters.put("default", CommandParameter.EMPTY_ARRAY);
        this.commandParameters.put("spawnPoint", new CommandParameter[]{
                CommandParameter.newType("spawnPoint", true, CommandParamType.POSITION)
        });
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.testPermission(sender)) {
            return false;
        }
        Level level;
        Vector3 pos;
        if (args.length == 0) {
            level = sender.getPosition().level;
            pos = sender.getPosition().round();
        } else if (args.length == 3) {
            level = sender.getServer().getDefaultLevel();
            CommandParser parser = new CommandParser(this,sender,args);
            try {
                pos = parser.parsePosition();
            } catch (NumberFormatException | CommandSyntaxException e1) {
                sender.sendMessage(new TranslationContainer("commands.generic.usage", "\n" + this.getCommandFormatTips()));
                return false;
            }
        } else {
            sender.sendMessage(new TranslationContainer("commands.generic.usage", "\n" + this.getCommandFormatTips()));
            return false;
        }
        level.setSpawnLocation(pos);
        DecimalFormat round2 = new DecimalFormat("##0.00");
        Command.broadcastCommandMessage(sender, new TranslationContainer("commands.setworldspawn.success", round2.format(pos.x),
                round2.format(pos.y),
                round2.format(pos.z)));
        return true;
    }
}
