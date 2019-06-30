package red.man10.man10auction.inventory

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import red.man10.man10auction.Man10Auction

class InventoryManager(private val plugin:Man10Auction){

    fun mainMenu(p:Player){
        val i = Bukkit.createInventory(null,54,plugin.title+"§0")


    }


    ///////////////////////////
    //inventory用ボタン作成の補助
    /////////////////////////
    fun button(title:String,lore:MutableList<String>,type:Material,d:Short):ItemStack{
        val i = ItemStack(type,1,d)
        val m = i.itemMeta
        m.displayName = title
        m.lore = lore
        i.itemMeta = m
        return i
    }
}