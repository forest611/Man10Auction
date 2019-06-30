package red.man10.man10auction

import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10auction.inventory.AuctionClickEvent
import red.man10.man10vaultapiplus.Man10VaultAPI
import red.man10.man10vaultapiplus.Man10VaultAPIPlus

class Man10Auction : JavaPlugin() {

    var title = ""
    lateinit var vaultapi : Man10VaultAPI
    lateinit var clickEvent: AuctionClickEvent

    override fun onEnable() {
        // Plugin startup logic
        saveDefaultConfig()

        title = config.getString("title")

        vaultapi = Man10VaultAPI("man10auction")

        clickEvent = AuctionClickEvent(this)



    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
