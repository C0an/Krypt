package cc.ryaan.krypt;

import cc.ryaan.krypt.listener.KryptListener;
import cc.ryaan.krypt.npc.NPC;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
public class Krypt {

    private final JavaPlugin javaPlugin;
    private final Set<NPC> npcList = new HashSet<>();

    public Krypt(JavaPlugin javaPlugin) {
        this.javaPlugin = javaPlugin;
        Bukkit.getPluginManager().registerEvents(new KryptListener(), javaPlugin);
    }

    public void registerNPC(NPC npc, boolean sendToPlayers) {
        npcList.add(npc);
        if(sendToPlayers) npc.spawn();
    }

    public void registerNPC(NPC npc) {
        registerNPC(npc, false);
    }

    public NPC getNPC(UUID uuid) {
        return npcList.stream().filter(npc -> npc.getUuid().equals(uuid)).findFirst().orElse(null);
    }

}
