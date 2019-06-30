package red.man10.man10auction.database

import net.milkbowl.vault.Vault
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder
import red.man10.man10auction.Man10Auction
import red.man10.man10vaultapiplus.Man10VaultAPIPlus
import red.man10.man10vaultapiplus.enums.TransactionType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class AuctionDataBase (private val plugin:Man10Auction) {

    val pages = HashMap<Int, MutableList<ItemStack>>()

    /////////////////////////////
    //利益、返金データ取得
    ////////////////////////////
    fun getMoney(p: Player): Double {
        val mysql = MySQLManagerV2(plugin, "man10auction")

        val query = mysql.query("SELECT amount FROM user_money WHERE uuid='${p.uniqueId}';")

        val rs = query.rs
        rs.next()
        val amount = rs.getDouble("amount")
        query.close()
        rs.close()
        return amount
    }

    //////////////////////
    //引き出し
    ///////////////////
    fun withdraw(p:Player){
        plugin.vaultapi.givePlayerMoney(p.uniqueId,getMoney(p),TransactionType.WITHDRAW,"auction withdraw")
        resetMoney(p)
    }

    ////////////////////////////
    //利益をdbに保存
    //////////////////////////
    fun addMoney(p: Player, amount: Double) {
        val mysql = MySQLManagerV2(plugin, "man10auction")

        mysql.execute("UPDATE user_money set amount='${(amount)}' WHERE uuid='${p.uniqueId}';")

    }

    /////////////////////////
    //所持金データを0に
    /////////////////////////
    fun resetMoney(p: Player) {
        val mysql = MySQLManagerV2(plugin, "man10auction")
        mysql.execute("UPDATE user_money set amount='0' WHERE uuid='${p.uniqueId}';")
        Bukkit.getLogger().info("reset ${p.name} money")
    }

    /////////////////////////
    //所持金データを作成
    ////////////////////////
    fun createMoneyData(p: Player) {
        val mysql = MySQLManagerV2(plugin, "man10auction")

        mysql.execute("INSERT INTO user_money VALUES(${p.name},${p.uniqueId},0);")
    }

    ///////////////////////
    //所持金データが0以上かどうか
    ///////////////////////
    fun isMoney(p: Player): Boolean {
        val mysql = MySQLManagerV2(plugin, "man10auction")

        val query = mysql.query("SELECT amount FROM user_money WHERE uuid='${p.uniqueId}';")
        val rs = query.rs
        rs.next()
        val amount = rs.getDouble("amount")
        query.close()
        rs.close()
        if (amount > 0) {
            return true
        }
        return false
    }

    ////////////////////////////////////////////////////////////////////
    ///////////////////////////
    //指定ページのアイテム取得
    ///////////////////////////
    fun loadItems(page:Int): MutableList<ItemStack> {
        val mysql = MySQLManagerV2(plugin,"man10auction")
        val items = mutableListOf<ItemStack>()

        val query = mysql.query("SELECT * FROM exhibition ORDER BY date DESC ${page * 45}, 45;")

        val rs = query.rs

        while (rs.next()){
            items.add(getDisplayItem(Bukkit.getPlayer(rs.getString("player")), itemFromBase64(rs.getString("item_stack"))!!
                    ,rs.getDouble("reserve_price"),rs.getDouble("buyout_price"),rs.getInt("id")))
        }

        rs.close()
        query.close()
        return items
    }

    ///////////////////////////
    //最高入札額を取得(出品者,id)
    fun getBestBidPrice(p: Player, id: Int): Double {

        val mysql = MySQLManagerV2(plugin, "man10auction")

        val query = mysql.query("SELECT bid_amount FROM bid WHERE exhibition_player='$p'" +
                " and id='$id' ORDER BY bid_amount DESC LIMIT 1;")
        val rs = query.rs
        rs.next()
        val amount = rs.getDouble("bid_amount")
        query.close()
        rs.close()

        return amount
    }

    /////////////
    //出品
    ////////////
    fun exhibition(p: Player, stack: ItemStack, reserve: Double, buyout: Double) {
        val mysql = MySQLManagerV2(plugin,"man10auction")
        val id = Random.nextInt(10000)
        mysql.execute("INSERT INTO exhibition VALUES($id,${p.name},${p.uniqueId},${itemToBase64(stack)},$reserve,$buyout,now());")

        if (pages[pages.size - 1]!!.size == 45){
            pages[pages.size] = mutableListOf<ItemStack>()
        }
        pages[pages.size -1]!!.add(getDisplayItem(p,stack,reserve,buyout,id))
    }


    ///////////////////////
    //入札
    ///////////////////////
    fun bid(stack:ItemStack,p:Player,amount: Double):Boolean{

        val data = getDisplayData(stack)

        if(data.now>amount){
            return false
        }

        if(data.buyout<=amount){
            Bukkit.getLogger().info("buyout")
            return true
        }

        val mysql = MySQLManagerV2(plugin,"man10auction")

        val query = mysql.query("SELECT * FROM bid WHERE exhibition_player='${data.player!!.name}' and" +
                " bid_player='${p.name}' and id='${data.id}';")

        val rs = query.rs

        if (rs.next()){
            cancelBid(p,data.player!!,data.id)
        }

        mysql.execute("INSERT INTO bid VALUES(${data.id},${data.player!!.name},${p.name},$amount,now());")

        plugin.vaultapi.takePlayerMoney(p.uniqueId,amount, TransactionType.UNKNOWN,"auction bid")

        return true

    }

    /////////////////////
    //入札取り消し
    //////////////////
    fun cancelBid(p:Player,ep:Player,id:Int)
    {
        Bukkit.getLogger().info("cancel bid")

        val mysql = MySQLManagerV2(plugin,"man10auction")

        val query = mysql.query("SELECT amount FROM bid WHERE exhibition_player='${ep.name}', and id='$id'" +
                " and bid_player='${p.name}';")

        val rs = query.rs
        rs.next()

        addMoney(p,rs.getDouble("amount"))

        query.close()
        rs.close()

        mysql.execute("DELETE FROM bid WHERE exhibition_player='${ep.name}', and id='$id' and bid_player='${p.name}';")
    }

    ///////////////////////////
    //落札 ep...出品者
    /////////////////////////
    fun successfuBid(p: Player,ep:Player,item: ItemStack,buyout:Boolean){
        val data =getDisplayData(item)

        ////////////
        //即決、オークションじゃない
        ////////
        if(buyout){
            addMoney(ep,data.buyout)
        }else{
            addMoney(ep,data.now)
        }

        val mysql = MySQLManagerV2(plugin,"man10auction")
        mysql.execute("DELETE FROM exhibition WHERE id='${data.id}' and player='${data.player!!.name}';")
        mysql.execute("DELETE FROM bid WHERE exhibition_player='${ep.name}', and id='${data.id}' and bid_player='${p.name}';")

        val query = mysql.query("SELECT item_stack FROM exhibition WHERE id='${data.id}' and player='${data.player!!.name}';")

        val rs = query.rs
        rs.next()

        p.inventory.addItem(itemFromBase64(rs.getString("item_stack")))
    }

    ////////////////////////////////
    //落札したアイテムの入札を消す、返金
    /////////////////////////////////
    fun cancelAnyBid(item: ItemStack){
        val mysql = MySQLManagerV2(plugin,"man10auction")


    }


//////////////////////////////////////////////////////////

    /////////////////////////
    //表示用アイテムからデータ取得
    //////////////////////
    fun getDisplayData(stack:ItemStack): ItemData {
        val lore = stack.itemMeta.lore

        val data = ItemData()

        data.player = Bukkit.getPlayer(lore[lore.size-4])
        data.id = lore[lore.size - 1].toInt()


        if(lore.indexOf("§4§lこのアイテムはオークションではありません！") >=0){
            Bukkit.getLogger().info("is not auction")
            data.now = -1.0
            data.buyout = lore[lore.size - 3].toDouble()

        }else{
            Bukkit.getLogger().info("is auction")
            data.buyout = lore[lore.size - 2].toDouble()
            data.now = lore[lore.size - 3].toDouble()
        }

        return data

    }

    class ItemData{
        var player:Player? = null
        var id = 0
        var buyout = 0.0
        var now = 0.0
    }

    /////////////////
    //表示用アイテム
    /////////////////
    fun getDisplayItem(p: Player, stack: ItemStack, reserve: Double, buyout: Double, id: Int): ItemStack {
        val item = stack

        val meta = item.itemMeta
        val lore = meta.lore
        lore.add("§6§l出品者：§e§l${p.name}")
        if (reserve == -1.00) {
            lore.add("§6§l出品価格：§e§l$buyout")
            lore.add("§4§lこのアイテムはオークションではありません！")
        } else {
            lore.add("§6§l現在価格：§e§l${getBestBidPrice(p, id)}")
            lore.add("§6§l即決価格：§e§l$buyout")

        }
        lore.add("§6§l出品ID:§e§l$id")

        meta.lore = lore

        return item
    }

    ///////////////////////////////
    //base 64
    fun itemFromBase64(data: String): ItemStack? = try {
        val inputStream = ByteArrayInputStream(Base64Coder.decodeLines(data))
        val dataInput = BukkitObjectInputStream(inputStream)
        val items = arrayOfNulls<ItemStack>(dataInput.readInt())

        // Read the serialized inventory
        for (i in items.indices) {
            items[i] = dataInput.readObject() as ItemStack
        }

        dataInput.close()
        items[0]
    } catch (e: Exception) {
        null
    }

    @Throws(IllegalStateException::class)
    fun itemToBase64(item: ItemStack): String {
        try {
            val outputStream = ByteArrayOutputStream()
            val dataOutput = BukkitObjectOutputStream(outputStream)
            val items = arrayOfNulls<ItemStack>(1)
            items[0] = item
            dataOutput.writeInt(items.size)

            for (i in items.indices) {
                dataOutput.writeObject(items[i])
            }

            dataOutput.close()
            val base64: String = Base64Coder.encodeLines(outputStream.toByteArray())

            return base64

        } catch (e: Exception) {
            throw IllegalStateException("Unable to save item stacks.", e)
        }
    }
}
