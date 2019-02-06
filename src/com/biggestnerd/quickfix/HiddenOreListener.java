package com.biggestnerd.quickfix;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.github.devotedmc.hiddenore.events.HiddenOreEvent;
import com.github.devotedmc.hiddenore.events.HiddenOreGenerateEvent;

public class HiddenOreListener implements Listener {

	List<String> worlds;
	
	public HiddenOreListener(List<String> worlds) {
		this.worlds = worlds;
		if(worlds == null) {
			worlds = new ArrayList<String>();
		}
	}
	
	@EventHandler
	public void onHiddenOreGenerate(HiddenOreGenerateEvent event) {
		if(!worlds.contains(event.getBlock().getWorld().getName().toLowerCase())) {
			event.setCancelled(true);
		}
	}
	
	@EventHandler
	public void onHiddenOre(HiddenOreEvent event) {
		if(!worlds.contains(event.getDropLocation().getWorld().getName().toLowerCase())) {
			event.setCancelled(true);
		}
	}
}
