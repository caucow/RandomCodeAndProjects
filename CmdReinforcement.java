/*
 * Copyright (c) 2021, caucow (https://github.com/caucow). All rights reserved.
 * Names and mappings provided by ModCoderPack (c) their respective creators.
 * The cluster that is Minecraft (c) Mojang, Microsoft.
 * DO NOT ALTER, MOVE, OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER
 * 
 * The code below is subject to free use under a modified version of
 * the DON'T BE A DICK PUBLIC LICENSE; all ambiguities, contradictions, and/or
 * subjective opinions may only be clarified or otherwise resolved by the
 * copyright holder. If you are not sure if you can use the copyrighted content
 * and are unable to reach the copyright holder, fuck you, you can't use the
 * copyrighted content.
 * 
 * > DON'T BE A DICK PUBLIC LICENSE
 * > TERMS AND CONDITIONS FOR COPYING, DISTRIBUTION AND MODIFICATION
 * 
 * 1. Do whatever you like with the original work, just don't be a dick.
 * 
 *    Being a dick includes - but is not limited to - the following instances:
 * 
 *  1a. Outright copyright infringement - Don't just copy this and change the name.
 *  1b. Selling the unmodified original with no work done what-so-ever, that's REALLY being a dick.
 *  1c. Modifying the original work to contain hidden harmful content. That would make you a PROPER dick.
 *  1d. Aligning with or associating with, promoting, encouraging, not genuinely denouncing, or otherwise
 *      not distancing yourself or your name from [the USA alliance or its members or associates on
 *      CivRealms (CivRealms.com; /r/CivRealms) between spring 2020 and summer 2021].
 * 
 * 2. If you become rich through modifications, related works/services, or supporting the original work,
 * share the love. Only a dick would make loads off this work and not buy the original work's
 * creator(s) a pint.
 * 
 * 3. Code is provided with no warranty. Using somebody else's code and bitching when it goes wrong makes
 * you a DONKEY dick. Fix the problem yourself. A non-dick would submit the fix back.
 * 
 * 4. If you qualify as "being a dick" according to the non-exhaustive and/or subjective list(s) above,
 * or if you currently, formerly, or will in the future fit the description in section 1d, fuck you,
 * you cannot use, copy, modify, redistribute, or [derive new content from] the copyrighted content in
 * any form unless the copyright holder says so.
 */

package com.caucraft.mods;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.caucraft.config.JsonConfig;
import com.caucraft.event.ChatEvent;
import com.caucraft.event.listener.IBlockChangeListener;
import com.caucraft.event.listener.IChatListener;
import com.caucraft.event.listener.IPacketListener;
import com.caucraft.event.listener.IRenderListener;
import com.caucraft.event.listener.ITickListener;
import com.caucraft.event.listener.IWorldChangeListener;
import com.caucraft.renderer.CCraftRenderer;
import com.caucraft.util.IToggleableCCraftCmd;
import com.caucraft.util.Util;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.network.play.client.CPacketPlayerDigging.Action;
import net.minecraft.network.play.server.SPacketEntityProperties;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.network.play.server.SPacketEntityProperties.Snapshot;
import net.minecraft.network.play.server.SPacketHeldItemChange;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.World;

/**
 * @author caucow
 */
public class CmdReinforcement extends CCraftCmd implements IToggleableCCraftCmd, ITickListener, IChatListener, IWorldChangeListener, IRenderListener, IPacketListener, IBlockChangeListener {

	private static Pattern reinfPattern = Pattern.compile("(\u00a7.)*\u00a7(?<color>.)(Reinforced|Locked) (?<pct>\\d+(\\.\\d+)?)% with (?<tier>\\S+)(.*, group: (?<group>\\S+))?.*");
	private static Pattern locPattern = Pattern.compile(".*Location: \\[.+ (?<x>-?\\d+) (?<y>-?\\d+) (?<z>-?\\d+)\\].+");
	private boolean enabled;
	private LinkedHashMap<BlockPos, QueuedPacket> clickQueue;
	private LinkedHashMap<BlockPos, Reinforcement> reinfMap;
	private LinkedHashMap<BlockPos, Integer> destroyMap;
	private int ticks;
	
	public CmdReinforcement() {
		clickQueue = new LinkedHashMap<>();
		reinfMap = new LinkedHashMap<>();
		destroyMap = new LinkedHashMap<>();
	}

	@Override
	public void execute(String[] cmd, String cmdStr) {
		if (cmd.length == 1) {
			toggle();
		} else if (cmd[1].equalsIgnoreCase("clear")) {
			synchronized (this) {
				reinfMap.clear();
				clickQueue.clear();
			}
		} else {
			printCC("Invalid command arguments.");
		}
	}

	@Override
	public void help(String[] var1) {
		printCC("Usage:");
		printCC("reinforcement");
		printCC("\u00a7eToggle \u00a7fRenders reinforcement information on blocks.");
		printCC("reinforcement clear");
		printCC("Clears rendered reinforcements.");
	}

	@Override
	public String getSimpleHelp() {
		return "Renders reinforcement information on blocks.";
	}

	@Override
	public void getCmdSuggestions(List<String> sug, String cmdStr, String[] cmdSplit) {
		if (Util.argsPMatch(cmdStr, "reinforcement")) {
			sug.add("reinforcement");
		}
		if (Util.argsPMatch(cmdStr, "reinforcement clear")) {
			sug.add("reinforcement clear");
		}
	}

	@Override
	public boolean toggle() {
		enabled = !enabled;
		return false;
	}

	@Override
	public boolean enable() {
		enabled = true;
		return false;
	}

	@Override
	public boolean disable() {
		enabled = false;
		return false;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public String getTag() {
		return enabled ? "Reinforcement" : null;
	}
	
	@Override
	public void configLoaded(JsonConfig c) {
		enabled = c.getBool("reinforcement.enabled", false);
	}

	@Override
	public void configSaved(JsonConfig c) {
		c.set("reinforcement.enabled", enabled);
	}

	@Override
	public void doRender(Vec3d lastPos, Vec3d pos, Vec3d part) {
		if (enabled) {
			BlockPos base = new BlockPos(pos);
			for (int x = -7; x <= 7; x++) {
				for (int y = -7; y <= 7; y++) {
					for (int z = -7; z <= 7; z++) {
						BlockPos here = base.add(x, y, z);
						Reinforcement reinf = reinfMap.get(here);
						if (reinf != null) {
							int rgb = MathHelper.hsvToRGB(reinf.pct / 3.0F, 1.0F, 1.0F);
							CCraftRenderer.fillBox(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, 64, 3.0F, true, 1.001, here.getX() + 0.5, here.getY() + 0.5, here.getZ() + 0.5);
							
							GlStateManager.pushMatrix();
							GlStateManager.enableTexture2D();
							int color = reinf.friendly ? 0x8080FF80 : 0x80800000;
							String tier = reinf.tier;
							String pct = String.format("%.2f%%", reinf.pct * 100.0F);
							String group = reinf.group;
							float toff = -mc.fontRendererObj.getStringWidth(tier) / 2.0F;
							float poff = -mc.fontRendererObj.getStringWidth(pct) / 2.0F;
							float goff = -mc.fontRendererObj.getStringWidth(group) / 2.0F;
							float scale = 0.01F;
							GlStateManager.translate(
									here.getX() + 0.5 + CCraftRenderer.transX,
									here.getY() + 0.5 + CCraftRenderer.transY,
									here.getZ() + 0.5 + CCraftRenderer.transZ);

							GlStateManager.glNormal3f(0.0F, 1.0F, 0.0F);
							GlStateManager.disableLighting();
							GlStateManager.depthMask(false);

							GlStateManager.enableBlend();
							GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

							GlStateManager.pushMatrix();
							GlStateManager.translate(0.0, 0.0, -0.505);
							GlStateManager.scale(-scale, -scale, scale);
							mc.fontRendererObj.drawString(tier, toff, 0F, color, false);
							mc.fontRendererObj.drawString(pct, poff, -10F, color, false);
							if (group != null) {
								mc.fontRendererObj.drawString(group, goff, 10F, color, false);
							}
							GlStateManager.popMatrix();
							GlStateManager.rotate(90F, 0F, 1F, 0F);
							GlStateManager.pushMatrix();
							GlStateManager.translate(0.0, 0.0, -0.505);
							GlStateManager.scale(-scale, -scale, scale);
							mc.fontRendererObj.drawString(tier, toff, 0F, color, false);
							mc.fontRendererObj.drawString(pct, poff, -10F, color, false);
							if (group != null) {
								mc.fontRendererObj.drawString(group, goff, 10F, color, false);
							}
							GlStateManager.popMatrix();
							GlStateManager.rotate(90F, 0F, 1F, 0F);
							GlStateManager.pushMatrix();
							GlStateManager.translate(0.0, 0.0, -0.505);
							GlStateManager.scale(-scale, -scale, scale);
							mc.fontRendererObj.drawString(tier, toff, 0F, color, false);
							mc.fontRendererObj.drawString(pct, poff, -10F, color, false);
							if (group != null) {
								mc.fontRendererObj.drawString(group, goff, 10F, color, false);
							}
							GlStateManager.popMatrix();
							GlStateManager.rotate(90F, 0F, 1F, 0F);
							GlStateManager.pushMatrix();
							GlStateManager.translate(0.0, 0.0, -0.505);
							GlStateManager.scale(-scale, -scale, scale);
							mc.fontRendererObj.drawString(tier, toff, 0F, color, false);
							mc.fontRendererObj.drawString(pct, poff, -10F, color, false);
							if (group != null) {
								mc.fontRendererObj.drawString(group, goff, 10F, color, false);
							}
							GlStateManager.popMatrix();
							GlStateManager.rotate(90F, 1F, 0F, 0F);
							GlStateManager.pushMatrix();
							GlStateManager.translate(0.0, 0.0, -0.505);
							GlStateManager.scale(-scale, -scale, scale);
							mc.fontRendererObj.drawString(tier, toff, 0F, color, false);
							mc.fontRendererObj.drawString(pct, poff, -10F, color, false);
							if (group != null) {
								mc.fontRendererObj.drawString(group, goff, 10F, color, false);
							}
							GlStateManager.popMatrix();
							GlStateManager.rotate(180F, 1F, 0F, 0F);
							GlStateManager.pushMatrix();
							GlStateManager.translate(0.0, 0.0, -0.505);
							GlStateManager.scale(-scale, -scale, scale);
							mc.fontRendererObj.drawString(tier, toff, 0F, color, false);
							mc.fontRendererObj.drawString(pct, poff, -10F, color, false);
							if (group != null) {
								mc.fontRendererObj.drawString(group, goff, 10F, color, false);
							}
							GlStateManager.popMatrix();
							
							GlStateManager.disableTexture2D();
							GlStateManager.popMatrix();
						}
					}
				}
			}
		}
	}

	@Override
	public void worldChanged(World world) {
		synchronized (this) {
			clickQueue.clear();
			reinfMap.clear();
		}
	}

	@Override
	public void preTick() {
		if (enabled) {
			ticks++;
			if (!clickQueue.isEmpty()) {
				synchronized (this) {
					QueuedPacket qp;
					while (!clickQueue.isEmpty() && (qp = clickQueue.entrySet().iterator().next().getValue()).expire <= ticks) {
						clickQueue.remove(qp.extra);
						reinfMap.remove(qp.extra);
					}
				}
			}
			if (!destroyMap.isEmpty()) {
				synchronized (this) {
					Map.Entry<BlockPos, Integer> ent;
					while (!destroyMap.isEmpty() && (ent = destroyMap.entrySet().iterator().next()).getValue() <= ticks) {
						destroyMap.remove(ent.getKey());
						if (mc.world != null) {
							Block b = mc.world.getBlockState(ent.getKey()).getBlock();
							if (b == Blocks.AIR) {
								reinfMap.remove(ent.getKey());
							}
						}
					}
				}
			}
		}
	}

	@Override
	public void doTick() {
	}

	@Override
	public void postTick() {
	}
	

	@Override
	public void packetSent(Packet p) {
		if (enabled) {
			if (p instanceof CPacketPlayerDigging) {
				CPacketPlayerDigging digp = (CPacketPlayerDigging)p;
				if (digp.getAction() == Action.START_DESTROY_BLOCK) {
					synchronized (this) {
						clickQueue.put(digp.getPosition(), new QueuedPacket(PacketType.DIG, ticks + 20, digp.getPosition()));
					}
				}
			}
		}
	}

	@Override
	public void packetReceived(Packet p) {
		if (enabled) {
		}
	}

	@Override
	public void chatMessage(ChatEvent evt) {
		if (enabled) {
			Matcher m = reinfPattern.matcher(evt.getFormattedText());
			if (m.find()) {
				String color = m.group("color");
				String pct = m.group("pct");
				String tier = m.group("tier");
				String group = m.group("group");
				LinkedList<ITextComponent> q = new LinkedList<>();
				q.add(evt.getTextComponent());
				while (!q.isEmpty()) {
					ITextComponent text = q.poll();
					Style textStyle = text.getStyle();
					if (textStyle != null) {
						HoverEvent hevt = textStyle.getHoverEvent();
						if (hevt != null && hevt.getAction() == HoverEvent.Action.SHOW_TEXT) {
							Matcher m2 = locPattern.matcher(hevt.getValue().getFormattedText());
							if (m2.find()) {
								synchronized (this) {
									BlockPos pos = new BlockPos(Integer.parseInt(m2.group("x")), Integer.parseInt(m2.group("y")), Integer.parseInt(m2.group("z")));
									clickQueue.remove(pos);
									reinfMap.put(pos, new Reinforcement("2aA".indexOf(color.charAt(0)) != -1, Float.parseFloat(pct) / 100.0F, tier, group));
									evt.setChatLineId(ChatEvent.getRegisteredChatId("reinf"));
								}
								break;
							}
						}
					}
					q.addAll(text.getSiblings());
				}
			}
		}
	}

	@Override
	public Class<? extends Packet>[] getSendTypes() {
		return new Class[] { CPacketPlayerDigging.class };
	}

	@Override
	public Class<? extends Packet>[] getReceiveTypes() {
		return new Class[] { SPacketHeldItemChange.class };
	}
	
	private static class QueuedPacket {
		
		public final PacketType type;
		public final int expire;
		public final BlockPos extra;
		
		public QueuedPacket(PacketType type, int expire, BlockPos extra) {
			this.type = type;
			this.expire = expire;
			this.extra = extra;
		}
	}
	
	private static enum PacketType {
		SETSLOT(),
		DIG();
	}
	
	private static class Reinforcement {
		
		public final boolean friendly;
		public final float pct;
		public final String tier;
		public final String group;
		
		public Reinforcement(boolean friendly, float pct, String tier, String group) {
			this.friendly = friendly;
			this.pct = pct;
			this.tier = tier;
			this.group = group;
		}
	}

	@Override
	public void chunkChanged(World w, int cx, int cz) {
		if (enabled) {
			
		}
	}

	@Override
	public void blockChanged(World w, BlockPos pos) {
		if (enabled) {
			Block type = w.getBlockState(pos).getBlock();
			if (type == Blocks.AIR && reinfMap.containsKey(pos)) {
				synchronized (this) {
					destroyMap.put(pos instanceof MutableBlockPos ? new BlockPos(pos) : pos, ticks + 40);
				}
			} else if (type != Blocks.AIR && destroyMap.containsKey(pos)) {
				synchronized (this) {
					destroyMap.remove(pos);
				}
			}
		}
	}

	@Override
	public void blockRangeChanged(World w, BlockPos min, BlockPos max) {
		if (enabled) {
			MutableBlockPos pos = new MutableBlockPos();
			for (int x = min.getX(); x <= max.getX(); x++) {
				for (int y = min.getY(); y <= max.getY(); y++) {
					for (int z = min.getZ(); z <= max.getZ(); z++) {
						pos.setPos(x, y, z);
						blockChanged(w, pos);
					}
				}
			}
		}
	}

	@Override
	public void chunkUnloaded(World w, int cx, int cz) {
		// TODO Auto-generated method stub
		
	}
}
