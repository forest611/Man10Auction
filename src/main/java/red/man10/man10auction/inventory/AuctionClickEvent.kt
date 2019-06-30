package red.man10.man10auction.inventory

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import red.man10.man10auction.Man10Auction

class AuctionClickEvent(private val plugin:Man10Auction): Listener {


    /////////////////////////////////////
    //click event
    /////////////////////////////////////
    @EventHandler
    fun clickEvent(e:InventoryClickEvent){
        if (e.inventory.title.indexOf(plugin.title) == -1){ return }



    }

    fun mainMenu(e: InventoryClickEvent){

    }

}