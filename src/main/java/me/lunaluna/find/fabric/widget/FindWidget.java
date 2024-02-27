package me.lunaluna.find.fabric.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.logging.log4j.util.Strings;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;

public class FindWidget extends TextFieldWidget {

    private static FindWidget instance;
    public static String search = "";

    public FindWidget(int x, int y) {
        super(MinecraftClient.getInstance().textRenderer, x, y, 174, 18, Text.empty());
        setText(search);
        setChangedListener(string -> search = string);
        instance = this;
    }

    public static FindWidget getInstance() {
        return instance;
    }

    private boolean matchString(String string, String searchText) {
        string = string.toLowerCase();

        if (searchText.startsWith("!")) {
            return string.equals(searchText.substring(1));
        } else {
            return Arrays.stream(searchText.split(" "))
                    .allMatch(string::contains);
        }
    }

    public boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        String searchText = getText().trim().toLowerCase();
        if (Strings.isBlank(searchText)) return false;

        return  matchItemId(stack, searchText) ||
                matchDisplayName(stack, searchText) ||
                matchNbtData(stack, searchText) ||
                matchBlockEntityTag(stack);
    }

    private boolean matchItemId(ItemStack stack, String searchText) {
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        return itemId.toString().toLowerCase().contains(searchText);
    }

    private boolean matchDisplayName(ItemStack stack, String searchText) {
        Text translatedText = Text.translatable(stack.getItem().getTranslationKey());
        return matchString(translatedText.getString(), searchText);
    }

    private boolean matchNbtData(ItemStack stack, String searchText) {
        NbtCompound nbt = stack.getNbt();
        if (nbt != null) {
            return matchNbtDisplayName(nbt, searchText) || matchEnchantments(nbt, searchText) || matchPotionEffects(nbt, searchText);
        }
        return false;
    }

    private boolean matchNbtDisplayName(NbtCompound nbt, String searchText) {
        if (nbt.contains("display", NbtElement.COMPOUND_TYPE)) {
            NbtCompound display = nbt.getCompound("display");
            if (display.contains("Name", NbtElement.STRING_TYPE)) {
                String displayName = parseDisplayNameFromJson(display.getString("Name"));
                return matchString(displayName, searchText);
            }
        }
        return false;
    }

    private boolean matchEnchantments(NbtCompound nbt, String searchText) {
        NbtList enchantmentTag = nbt.getList("Enchantments", 10);
        enchantmentTag.addAll(nbt.getList("StoredEnchantments", 10));
        for (int i = 0; i < enchantmentTag.size(); i++) {
            NbtCompound nbtTag = enchantmentTag.getCompound(i);
            Identifier id = Identifier.tryParse(nbtTag.getString("id"));
            if (id != null) {
                var enchantment = Registries.ENCHANTMENT.get(id);
                if (enchantment != null) {
                    String localizedName = Text.translatable(enchantment.getTranslationKey()).getString().toLowerCase();
                    if (matchString(localizedName, searchText) || matchString(localizedName + " " + nbtTag.getInt("lvl"), searchText)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean matchPotionEffects(NbtCompound nbt, String searchText) {
        NbtElement potionTag = nbt.get("Potion");
        if (potionTag != null && potionTag.getType() == NbtElement.STRING_TYPE) {
            String potionName = potionTag.asString();
            if (matchString(potionName, searchText))
                return true;

            Potion potion = Potion.byId(potionName);
            if (potion != null) {
                for (StatusEffectInstance effectInstance : potion.getEffects()) {
                    if (matchString(effectInstance.getEffectType().getName().getString(), searchText))
                        return true;
                }
            }
        }
        return false;
    }

    private boolean matchBlockEntityTag(ItemStack stack) {
        NbtCompound blockEntityTag = stack.getSubNbt("BlockEntityTag");
        if (blockEntityTag != null && blockEntityTag.contains("Items", 9)) {
            NbtList itemList = blockEntityTag.getList("Items", 10);
            for (int i = 0; i < itemList.size(); i++) {
                ItemStack subStack = ItemStack.fromNbt(itemList.getCompound(i));
                if (matches(subStack))
                    return true;
            }
        }
        return false;
    }

    private String parseDisplayNameFromJson(String displayNameJson) {
        StringBuilder displayName = new StringBuilder();
        try {
            JsonElement element = JsonParser.parseString(displayNameJson);
            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                if (obj.has("text")) {
                    displayName.append(obj.get("text").getAsString());
                }
                if (obj.has("extra")) {
                    JsonArray extras = obj.getAsJsonArray("extra");
                    for (JsonElement extraElement : extras) {
                        if (extraElement.isJsonObject()) {
                            JsonObject extraObj = extraElement.getAsJsonObject();
                            if (extraObj.has("text")) {
                                displayName.append(extraObj.get("text").getAsString());
                            }
                        }
                    }
                }
            } else if (element.isJsonArray()) {
                JsonArray jsonArray = element.getAsJsonArray();
                for (JsonElement el : jsonArray) {
                    JsonObject obj = el.getAsJsonObject();
                    if (obj.has("text")) {
                        displayName.append(obj.get("text").getAsString());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return displayName.toString();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible() || !isVisible()) return false;
        boolean inTextBox = mouseX >= (double)this.getX() && mouseX < (double)(this.getX() + this.width) && mouseY >= (double)this.getY() && mouseY < (double)(this.getY() + this.height);
        if (inTextBox && button == GLFW.GLFW_MOUSE_BUTTON_2) {
            setText("");
            return super.mouseClicked(mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_1);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public void renderFindWidget(DrawContext matrices, int mouseX, int mouseY, float delta) {
        if (visible()) {
            super.render(matrices, mouseX, mouseY, delta);
        }
    }

    public boolean visible() {
        return isFocused() || Strings.isNotBlank(search);
    }
}
