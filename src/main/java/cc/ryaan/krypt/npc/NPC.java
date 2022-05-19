package cc.ryaan.krypt.npc;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.stream.Collectors;

@Getter @Setter
public class NPC {

    private UUID uuid;
    private String name, displayName, texture, signature;
    private Location location;
    private List<UUID> viewers = new ArrayList<>();

    /*
        "transient" keywords do not save in the serialized Gson file.
        Read more at: https://sites.google.com/site/gson/gson-user-guide#TOC-Finer-Points-with-Objects
     */
    private transient EntityPlayer entityPlayer;
    private transient GameProfile gameProfile;
    private transient int entityId;

    public NPC(String name) {
        this.name = name;
        this.displayName = name;
        this.uuid = UUID.randomUUID();
    }

    public NPC(String displayName, UUID uuid, String name, String texture, String signature, Location location) {
        this.displayName = displayName;
        this.uuid = uuid;
        this.name = name;
        this.texture = texture;
        this.signature = signature;
        this.location = location;
    }

    public NPC(String displayName, UUID uuid, String name, String texture, String signature, List<Player> viewers) {
        this.displayName = displayName;
        this.uuid = uuid;
        this.name = name;
        this.texture = texture;
        this.signature = signature;
        this.viewers.addAll(viewers.stream().map(Player::getUniqueId).collect(Collectors.toList()));
    }

    /*
        With the magic power of NMS send a packet to spawn in
        a very simple Player NPC!
     */
    public void spawn() {
        if(getTexture() == null || getSignature() == null || getLocation() == null) {
            System.out.println("[NPC] No skin and/or Location provided - failed to spawn.");
            return;
        }

        MinecraftServer nmsServer = ((CraftServer) Bukkit.getServer()).getServer();
        WorldServer nmsWorld = ((CraftWorld) location.getWorld()).getHandle();
        EntityPlayer npc = new EntityPlayer(nmsServer, nmsWorld, new GameProfile(uuid, name), new PlayerInteractManager(nmsWorld));

        this.entityPlayer = npc;
        this.entityId = npc.getId();
        this.gameProfile = npc.getProfile();

        updateSkin(this.texture, this.signature);

        npc.setLocation(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        sendPacket(new PacketPlayOutNamedEntitySpawn(npc));

        rotateHead(location.getPitch(), location.getYaw());
        addToTablist();

    }

    /*
        For 1.8 clients to see the NPC we got to add them to the list - this will also hide the player.
     */
    public void addToTablist(){
        sendPacket(PacketPlayOutPlayerInfo.addPlayer(entityPlayer));
    }

    /*
        Delete the NPC - this will send a destroy packet to all available players
        and will remove the NPC from local memory.
     */
    public void delete() {
        destroy();
        npcs.remove(this);
    }

    /*
        Destroy the NPC for players
     */
    public void destroy(List<Player> players) {
        players.forEach(player -> sendPacket(getDestroyPacket(), player));
    }

    public void destroy(Player player) {
        destroy(Collections.singletonList(player));
    }

    public void destroy() {
        destroy(getViewers());
    }

    /*
        Allow the player to view the NPC - adding a viewer if there is no viewers set
        will destroy the npc for the public viewers.
     */
    public void addViewer(List<Player> players) {
        if(viewers.isEmpty()) destroy();
        viewers.addAll(players.stream().map(Player::getUniqueId).collect(Collectors.toList()));
        spawn();
    }

    public void addViewer(Player... players) {
        addViewer(Arrays.asList(players));
    }

    /*
        Disallow the player to view the NPC if they are
        already defined in the viewing list.
     */
    public void removeViewer(Player player) {
        if(!viewers.contains(player)) return;
        destroy(player);
        viewers.remove(player);
    }

    public void removeViewer(Player... players) {
        for (Player player : players) removeViewer(player);
    }

    /*
        Returns the amount of viewers an NPC is registered to - if there are no custom viewers set
        it will return the Bukkit Online Players.
     */
    public List<Player> getViewers() {
        if(viewers.isEmpty()) return new ArrayList<>(Bukkit.getOnlinePlayers());
        else return viewers.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /*
        Update the NPC for all the viewers
        This includes their Skin and Display Name changes.
     */
    public void updateNPC() {
        updateProfile();
        destroy();
        spawn();
    }

    /*/
        Send a regular PacketPlayOutNamedEntitySpawn packet
     */
    public void sendSpawnPacket() {
        sendPacket(new PacketPlayOutNamedEntitySpawn(entityPlayer));
    }

    /*
        Set the pitch and yaw the NPC will look at.
     */
    public void rotateHead(float pitch, float yaw, Location ogLocation) {
        PacketPlayOutEntityLook packetPlayOutEntityLook = new PacketPlayOutEntityLook(entityId, getFixRotation(yaw, ogLocation), (byte) pitch, true);
        PacketPlayOutEntityHeadRotation packetPlayOutEntityHeadRotation = new PacketPlayOutEntityHeadRotation();
        this.setField(packetPlayOutEntityHeadRotation, "a", entityId);
        this.setField(packetPlayOutEntityHeadRotation, "b", getFixRotation(yaw, ogLocation));
        this.sendPacket(packetPlayOutEntityLook);
        this.sendPacket(packetPlayOutEntityHeadRotation);
    }

    public void rotateHead(float pitch, float yaw) {
        PacketPlayOutEntityLook packetPlayOutEntityLook = new PacketPlayOutEntityLook(entityId, getFixRotation(yaw, this.location), (byte) pitch, true);
        PacketPlayOutEntityHeadRotation packetPlayOutEntityHeadRotation = new PacketPlayOutEntityHeadRotation();
        this.setField(packetPlayOutEntityHeadRotation, "a", entityId);
        this.setField(packetPlayOutEntityHeadRotation, "b", getFixRotation(yaw, this.location));
        this.sendPacket(packetPlayOutEntityLook);
        this.sendPacket(packetPlayOutEntityHeadRotation);
    }

    public void rotateHead() {
        PacketPlayOutEntityLook packetPlayOutEntityLook = new PacketPlayOutEntityLook(entityId, getFixRotation(location.getYaw(), this.location), (byte) location.getPitch(), true);
        PacketPlayOutEntityHeadRotation packetPlayOutEntityHeadRotation = new PacketPlayOutEntityHeadRotation();
        this.setField(packetPlayOutEntityHeadRotation, "a", entityId);
        this.setField(packetPlayOutEntityHeadRotation, "b", getFixRotation(location.getYaw(), this.location));
        this.sendPacket(packetPlayOutEntityLook);
        this.sendPacket(packetPlayOutEntityHeadRotation);

    }

    /*
        Set an item in the NPCs Inventory, you can find the slot numbers below.
        See: https://i.imgur.com/bCOZRIv.png
     */
    public void setItem(int slot, ItemStack item) {
        PacketPlayOutEntityEquipment packetPlayOutEntityEquipment = new PacketPlayOutEntityEquipment();
        setField(packetPlayOutEntityEquipment, "a", entityId);
        setField(packetPlayOutEntityEquipment, "b", slot);
        setField(packetPlayOutEntityEquipment, "c", item);
        sendPacket(packetPlayOutEntityEquipment);

        DataWatcher watcher = new DataWatcher(entityPlayer);
        watcher.a();

    }

    /*
        NPCs can support in-game animations via the PacketPlayOutAnimation
        packet provided kindly by NMS.
     */
    public void setAnimation(NPCAnimation npcAnimation) {
        PacketPlayOutAnimation packetPlayOutAnimation = new PacketPlayOutAnimation();
        setField(packetPlayOutAnimation, "a", getEntityId());
        setField(packetPlayOutAnimation, "b", npcAnimation.getId());
        sendPacket(packetPlayOutAnimation);
    }

    /*
        Give an NPC a custom potion effect using the
        MobEffect class.
     */
    public void setEffect(MobEffect mobEffect) {
        sendPacket(new PacketPlayOutEntityEffect(getEntityId(), mobEffect));
    }

    /*
       Set the NPC Action value - Crouched/Fire/Invisible etc...
    */
    public void setAction(Action action) {
        DataWatcher dataWatcher = new DataWatcher((Entity) null);
        dataWatcher.a(0, action.build());
        sendPacket(new PacketPlayOutEntityMetadata(getEntityId(), dataWatcher, true));
    }

    /*
       Reset the NPC Action value
    */
    public void resetAction() {
        DataWatcher dataWatcher = new DataWatcher((Entity) null);
        dataWatcher.a(0, 0x00);
        sendPacket(new PacketPlayOutEntityMetadata(getEntityId(), dataWatcher, true));
    }

    /*/
        Update a NPCs skin that was provided via a Property
     */
    public void setSkin(Property property) {
        this.texture = property.getValue();
        this.signature = property.getSignature();
    }

    /*
        Return their skin as a Property method
     */
    public Property getSkin() {
        if (gameProfile.getProperties().isEmpty()) return null;
        return (Property) gameProfile.getProperties().get("textures").toArray()[0];
    }

    /*
        Update Profile will update the NPC's skin via there Texture and Signature
        defined within the constructor.
     */
    public void updateProfile() {
        Property property = getSkin();
        gameProfile = new GameProfile(gameProfile.getId(), getDisplayName());
        if(property != null) updateSkin(property.getValue(), property.getSignature());

    }

    /*
        Set the skin properties in the Game Profile
     */
    public void updateSkin(String texture, String signature) {
        gameProfile.getProperties().put("textures", new Property("textures", texture, signature));
    }


    /*
        Already prepared packets that will frequently get used;
     */
    private PacketPlayOutEntityDestroy getDestroyPacket() {
        return new PacketPlayOutEntityDestroy(entityId);
    }

    /*
        Send a packet to all the available viewers.
     */
    private void sendPacket(Packet packet, Player player) {
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    }

    private void sendPacket(Packet packet) {
        getViewers().forEach(player -> sendPacket(packet, player));
    }

    /*
        Useful Reflection Things
     */

    private Object getField(Object obj, String fieldName) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void setField(Object obj, String fieldName, Object value) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /*
        Minecraft is funky so we need a fixed location.
     */
    private byte getFixRotation(float yawpitch, Location location) {
        return (byte) ((int) (location.getYaw() * 256.0F / 360.0F));
    }


    private float toDegree(double angle) {
        return (float) Math.toDegrees(angle);
    }

    private Vector getVector(org.bukkit.entity.Entity entity) {
        if (entity instanceof Player)
            return ((Player) entity).getEyeLocation().toVector();
        else
            return entity.getLocation().toVector();
    }

    /*
        Grab a cached version of a players skin (if they exist!) then set it to the NPC. [Requires refresh]
        We use minetools due to it having no rate limit and a built in cache system.
     */
    public void grabSkin(String username) {
        try {
            JSONParser jsonParser = new JSONParser();
            String reponse = getResponse("https://api.minetools.eu/uuid/" + username);
            JSONObject parsed = (JSONObject) jsonParser.parse(reponse);

            String uuid = (String) parsed.get("id");
            reponse = getResponse("https://api.minetools.eu/profile/" + uuid);
            parsed = (JSONObject) jsonParser.parse(reponse);
            JSONObject raw = (JSONObject) parsed.get("raw");
            JSONObject properties = (JSONObject) ((JSONArray) raw.get("properties")).get(0);
            String value = (String) properties.get("value");
            String signature = (String) properties.get("signature");

            setSkin(new Property("textures", value, signature));
        }catch (Exception e) {
            e.printStackTrace();
        }
    }


    private String getResponse(String _url){
        try {
            URL url = new URL(_url);
            URLConnection con = url.openConnection();
            InputStream in = con.getInputStream();
            String encoding = con.getContentEncoding();
            encoding = encoding == null ? "UTF-8" : encoding;
            return IOUtils.toString(in, encoding);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public enum NPCAnimation {
        /**
         * Makes npc swing his arm.
         */
        SWING_ARM(0),
        /**
         * Highlights the npc in red to mark that it has been damaged.
         */
        DAMAGE(1),
        /**
         * Moves npc's arm towards his mouth to eat food.
         */
        EAT_FOOD(3),
        /**
         * Displays criticial hit
         */
        CRITICAL_HIT(4),
        /**
         * Display magic critical hit
         */
        MAGIC_CRITICAL_HIT(5);

        private final int id;

        NPCAnimation(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }
    }

    @Getter
    public static class Action {

        private boolean fire, crouched, sprinting, invisible;
        private byte result = 0;

        public Action(boolean fire, boolean crouched, boolean sprinting, boolean invisible) {
            this.fire = fire;
            this.crouched = crouched;
            this.sprinting = sprinting;
            this.invisible = invisible;
        }

        public Action() {
        }


        public Action setFire(boolean fire) {
            this.fire = fire;
            return this;
        }


        public Action setCrouched(boolean crouched) {
            this.crouched = crouched;
            return this;
        }


        public Action setSprinting(boolean sprinting) {
            this.sprinting = sprinting;
            return this;
        }

        public Action setInvisible(boolean invisible) {
            this.invisible = invisible;
            return this;
        }

        public byte build() {
            result = 0;
            result = add(this.fire, (byte) 0x01);
            result = add(this.crouched, (byte) 0x02);
            result = add(this.sprinting, (byte) 0x08);
            result = add(this.invisible, (byte) 0x20);
            return result;
        }

        private byte add(boolean condition, byte amount) {
            return (byte) (result += (condition ? amount : 0x00));
        }
    }

}
