/*
 * Copyright (c) 2017 Markus "Aneko" Isberg
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package xyz.roolurker.colorify

import net.dv8tion.jda.core.AccountType
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.JDABuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.*
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import net.dv8tion.jda.core.managers.RoleManagerUpdatable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.awt.Color
import java.util.*

/**
 * Created with love by Aneko on 3/8/2017.
 */

class Colorify(token: String) : ListenerAdapter() {
	object Colors : Table() {
		val role_id = varchar("role_id", length = 50)
		val guild_id = varchar("guild_id", length = 50)
		val name = varchar("name", length = 50)
	}

	object Settings : Table() {
		val guild_id = varchar("guild_id", length = 50)
		var alias = varchar("alias", length = 50)
		var allow_multiple = bool("allow_multiple")
	}

	val reg: Regex = Regex("[ \n]")
	val aliasCache: HashMap<String, String> = HashMap()
	val allowsCache: HashMap<String, Boolean> = HashMap()

	init {
		transaction {
			if (!Colors.exists()) {
				SchemaUtils.create(Colors)
			}

			if (!Settings.exists()) {
				SchemaUtils.create(Settings)
			}
		}
	}

	val noPerms: String = "Sorry, I do not have permission to manage roles."
	val alreadyHasColor: String = "You already have a role, please remove it with `%$ remove` first!"
	val noColorGiven: String = "please specify a name, use `%$ list` for list of all available roles."
	val roleNotFound: String = "that role does not exist."
	val cannotAccessRole: String = "sorry, I cannot modify that role."
	val roleAddSuccess: String = "enjoy your new role!"
	val forbidden: String = "you do not have permission to use this command."
	val alreadyExists: String = "that name is already in use."
	val doesntExist: String = "that role doesn't exist."
	val creationSuccess: String = "all done, move the role to it's appropriate position if needed."
	val removalSucess: String = "successfully removed."
	val deletionSuccess: String = "Successfully deleted the specified color."
	val doesNotExistsRole: String = "The color existed in the database but not on the server (manually deleted?)\n" +
			"I deleted it from the database but no roles were affected."
	val tooManyColors: String = "Unable to create a new color because your server has too many roles."
	val invalidName: String = "Invalid name, the name should be less than 50 characters long and only contain ASCII characters."

	init {
		val bot = JDABuilder(AccountType.BOT)
				.setToken(token)
				.addListener(this)
				.buildBlocking()
		bot.presence.game = Game.of("!colorhelp")
	}

	override fun onMessageReceived(event: MessageReceivedEvent?) {
		if (event == null) return
		if (event.message.channelType != ChannelType.TEXT) return
		val msg: Message = event.message
		val c: String = msg.rawContent
		val split: List<String> = c.split(reg, limit = 2)
		val pars: List<String>
		if (split.isNotEmpty()) {
			if (split.size >= 2) {
				pars = split[1].split(reg)
			} else {
				pars = listOf()
			}
		} else {
			return
		}
		val guild: Guild = event.guild
		val chan: TextChannel = event.textChannel
		val member: Member = event.member
		val allowed: MutableList<Permission>? = chan.getPermissionOverride(guild.selfMember)?.allowed
		val denied: MutableList<Permission>? = chan.getPermissionOverride(guild.selfMember)?.denied
		if ((allowed != null
				&& (allowed.contains(Permission.MESSAGE_MANAGE)))
				|| member.hasPermission(Permission.MANAGE_ROLES)
				|| guild.selfMember.hasPermission(Permission.MESSAGE_MANAGE)
				&& ((denied == null || !denied.contains(Permission.MESSAGE_MANAGE)))) {
			when (split[0]) {
				"!addcolor" -> addColor(member, chan, guild, pars)
				"!delcolor" -> remColor(member, chan, guild, pars)
				"!colorhelp" -> colorHelp(member, chan)
				"!setalias" -> setAlias(member, chan, guild, pars)
				"!multicolor" -> multiToggle(member, chan, guild)
				else -> {
					getGuildAlias(guild) { alias ->
						if (split[0] == alias) {
							applyColor(member, chan, guild, pars)
						}
					}
				}
			}
		}
	}

	fun colorHelp(sender: Member, channel: TextChannel) {
		val emb: EmbedBuilder = EmbedBuilder()
		emb.setColor(Color.YELLOW)
		emb.setDescription("I only responds to channels I have \"Manage Messages\" permission in, " +
				"all other channels are ignored unless you have \"Manage Roles\" permission.\n" +
				"Bugs? questions? or even suggestions? add me on Discord: Aneko#0022 or just @ me if we have mutual servers.\n" +
				"**Important note:** You should never manually delete roles, use `!delcolor <name>` for that.\n\n" +
				"List of commands")
		emb.addField("!addcolor <name> <hex>", "Creates a new color role with no permissions and adds it to the list of available colors.\n" +
				"The name should only contain ASCII characters and cannot be longer than 50 characters.\n" +
				"Requires \"Manage Roles\" permission.", false)
		emb.addField("!delcolor <name>", "Deletes an existing color role.\n" +
				"Requires \"Manage Roles\" permission.", false)
		emb.addField("!color <name>", "Gives you a fresh new color.\n" +
				"You can also use `!color list` for a list of all the available colors or `!color remove` to remove your current color\n" +
				"Can be used by anyone", false)
		emb.addField("!setalias <name>", "Aliases the !color command to something else.\n" +
				"Requires \"Manage Roles\" permission.", false)
		emb.addField("!multicolor", "Toggles the usage of multiple colors at a time.\n" +
				"Requires \"Manage Roles\" permission.", false)
		sendMessage(emb.build(), channel, sender)
	}

	fun multiToggle(sender: Member, channel: TextChannel, guild: Guild) {
		if (!sender.hasPermission(Permission.MANAGE_ROLES)) {
			sendMessage("${sender.effectiveName}, $forbidden", channel, sender)
			return
		}

		var toggle: Boolean = false

		transaction {
			val query: Query = Settings.select { Settings.guild_id eq guild.id }
			val exists: Boolean = query.count() != 0
			if (!exists) {
				Settings.insert {
					it[Settings.guild_id] = guild.id
					it[Settings.allow_multiple] = true
					it[Settings.alias] = "_"
				}
				allowsCache[guild.id] = true
			} else {
				query.forEach {
					toggle = !it[Settings.allow_multiple]
					return@forEach
				}
				Settings.update({ Settings.guild_id eq guild.id }, null, { set ->
					set[Settings.allow_multiple] = toggle
				})
				allowsCache[guild.id] = toggle
			}
			sendMessage("${if (toggle) "Enabled" else "Disabled"} the usage of multiple colors", channel, sender)
		}
	}

	fun setAlias(sender: Member, channel: TextChannel, guild: Guild, pars: List<String>) {
		if (!sender.hasPermission(Permission.MANAGE_ROLES)) {
			sendMessage("${sender.effectiveName}, $forbidden", channel, sender)
			return
		}

		if (pars.isEmpty()) {
			sendMessage("Usage: `!setalias <name>`", channel, sender)
			return
		}

		val alias: String = pars[0]
		if (alias.length >= 49) {
			sendMessage(invalidName, channel, sender)
			return
		} else {
			alias.forEach { c ->
				if (c > 0x7F.toChar()) {
					sendMessage(invalidName, channel, sender)
					return
				}
			}
		}

		transaction {
			val query: Query = Settings.select { Settings.guild_id eq guild.id }
			val exists: Boolean = query.count() != 0
			if (!exists) {
				Settings.insert {
					it[Settings.guild_id] = guild.id
					it[Settings.allow_multiple] = false
					it[Settings.alias] = alias
				}
			} else {
				Settings.update({ Settings.guild_id eq guild.id }, null, { set ->
					set[Settings.alias] = alias
				})
				aliasCache[guild.id] = alias
			}
			sendMessage("Alias $alias successfully set", channel, sender)
		}
	}

	fun remColor(sender: Member, channel: TextChannel, guild: Guild, pars: List<String>) {
		if (!sender.hasPermission(Permission.MANAGE_ROLES)) {
			sendMessage("${sender.effectiveName}, $forbidden", channel, sender)
			return
		}

		if (!guild.selfMember.hasPermission(Permission.MANAGE_ROLES)) {
			sendMessage(noPerms, channel, sender)
			return
		}

		if (pars.isEmpty()) {
			sendMessage("Usage: `!delcolor <name>`", channel, sender)
			return
		}

		val roleName: String = pars[0]

		transaction {
			val query: Query = Colors.select { Colors.guild_id eq guild.id and (Colors.name eq roleName) }
			val exists: Boolean = query.count() != 0
			if (!exists) {
				sendMessage(doesntExist, channel, sender)
			}
			query.forEach { q ->
				val roles: List<Role> = guild.roles.filter { q[Colors.role_id] == it.id }
				if (roles.isEmpty()) {
					Colors.deleteWhere { Colors.guild_id eq guild.id and (Colors.name eq roleName) }
					sendMessage(doesNotExistsRole, channel, sender)
				}
				roles.forEach { r ->
					if (guild.selfMember.canInteract(r)) {
						Colors.deleteWhere { Colors.guild_id eq guild.id and (Colors.name eq roleName) }
						r.delete().queue({
							sendMessage(deletionSuccess, channel, sender)
						})
						return@transaction
					} else {
						sendMessage(cannotAccessRole, channel, sender)
						return@transaction
					}
				}
			}
		}
	}

	fun addColor(sender: Member, channel: TextChannel, guild: Guild, pars: List<String>) {
		if (!sender.hasPermission(Permission.MANAGE_ROLES)) {
			sendMessage("${sender.effectiveName}, $forbidden", channel, sender)
			return
		}

		if (!guild.selfMember.hasPermission(Permission.MANAGE_ROLES)) {
			sendMessage(noPerms, channel, sender)
			return
		}

		if (guild.roles.size > 200) {
			sendMessage(tooManyColors, channel, sender)
			return
		}

		val sent: String = sender.effectiveName

		if (pars.size != 2) {
			sendMessage("Usage: `!addcolor <name> <hex>`", channel, sender)
			return
		}

		val roleName: String = pars[0]
		if (roleName.length >= 49) {
			sendMessage(invalidName, channel, sender)
			return
		} else {
			roleName.forEach { c ->
				if (c > 0x7F.toChar()) {
					sendMessage(invalidName, channel, sender)
					return
				}
			}
		}

		val clr: Color
		try {
			clr = Color.decode(pars[1])
		} catch (e: NumberFormatException) {
			sendMessage("Invalid hex color code.", channel, sender)
			return
		}

		alreadyExists(guild, roleName, { yes ->
			if (yes) {
				sendMessage("$sent, $alreadyExists", channel, sender)
			} else {
				guild.controller.createRole().queue({ newRole ->
					val man: RoleManagerUpdatable = newRole.managerUpdatable
					man.colorField.value = clr
					man.nameField.value = roleName
					man.permissionField.value = 0x00000000
					man.update().queue({
						transaction {
							Colors.insert {
								it[role_id] = newRole.id
								it[guild_id] = guild.id
								it[name] = roleName
							}
						}
						sendMessage(creationSuccess, channel, sender)
					})
				})
			}
		})
	}

	fun applyColor(sender: Member, channel: TextChannel, guild: Guild, pars: List<String>) {
		if (!guild.selfMember.hasPermission(Permission.MANAGE_ROLES)) {
			sendMessage(noPerms, channel, sender)
			return
		}

		val name: String = sender.effectiveName

		if (pars.isEmpty()) {
			getGuildAlias(guild) { alias ->
				sendMessage("$name, ${noColorGiven.replace("%$", alias)}", channel, sender)
			}
			return
		}

		transaction {
			var role: Role? = null
			val query = Colors.select { Colors.guild_id eq guild.id }
			sender.roles.forEach { currentRole ->
				if (query.any { it[Colors.role_id] == currentRole.id }) {
					role = currentRole
				}
			}
			if (pars[0] == "list") {
				val emb: EmbedBuilder = EmbedBuilder()
				emb.setColor(Color.YELLOW)
				emb.setTitle("List of available roles")
				var res: String = ""
				query.forEach { row ->
					res += (row[Colors.name] + " ")
				}
				if (res.length >= 2000) {
					emb.setDescription(res.substring(0, 2000))
				} else {
					emb.setDescription(res)
				}
				sendMessage(emb.build(), channel, sender)
				return@transaction
			}

			doesAllowMultiple(guild) { allows ->
				if (role != null) {
					if (!guild.selfMember.canInteract(role)) {
						sendMessage("$name, $cannotAccessRole", channel, sender)
						return@doesAllowMultiple
					}
					if (pars[0] == "remove") {
						if (allows) {
							if (pars.size == 2) {
								sender.roles.filter { it.name == pars[1] }.forEach {
									guild.controller.removeRolesFromMember(sender, it).queue({
										sendMessage("$name, $removalSucess", channel, sender)
									})
								}
								return@doesAllowMultiple
							} else {
								sendMessage("$name, please specify the role to remove", channel, sender)
								return@doesAllowMultiple
							}
						} else {
							guild.controller.removeRolesFromMember(sender, role).queue({
								sendMessage("$name, $removalSucess", channel, sender)
							})
							return@doesAllowMultiple
						}
					} else if (!allows) {
						getGuildAlias(guild) { alias ->
							sendMessage("$name, ${alreadyHasColor.replace("%$", alias)}", channel, sender)
						}
						return@doesAllowMultiple
					}
				}

				Colors.select { Colors.guild_id eq guild.id and (Colors.name eq pars[0]) }.forEach { row ->
					val i: List<Role> = guild.roles.filter { it.id == row[Colors.role_id] }
					if (i.isNotEmpty()) {
						val foundRole: Role = i[0]
						if (guild.selfMember.canInteract(foundRole)) {
							guild.controller.addRolesToMember(sender, foundRole).queue({
								sendMessage("$name, $roleAddSuccess", channel, sender)
							})
							return@doesAllowMultiple
						} else {
							sendMessage(cannotAccessRole, channel, sender)
							return@doesAllowMultiple
						}
					} else {
						return@forEach
					}
				}
				sendMessage("$name, $roleNotFound", channel, sender)
			}
		}
	}

	fun doesAllowMultiple(guild: Guild, callback: (Boolean) -> Unit) {
		var result: Boolean = false
		if (allowsCache[guild.id] == null) {
			transaction {
				val query: Query = Settings.select { Settings.guild_id eq guild.id }
				val exists: Boolean = query.count() != 0
				if (exists) {
					query.forEach {
						result = it[Settings.allow_multiple]
					}
				}
				callback.invoke(result)
				allowsCache.put(guild.id, exists)
			}
		} else {
			callback.invoke(allowsCache[guild.id]!!)
		}
	}

	fun getGuildAlias(guild: Guild, callback: (String) -> Unit) {
		if (aliasCache[guild.id] == null) {
			transaction {
				var result: String = "!color"
				val query: Query = Settings.select { Settings.guild_id eq guild.id }
				val exists: Boolean = query.count() != 0
				if (exists) {
					query.forEach {
						if (it[Settings.alias] != "_") {
							result = it[Settings.alias]
						}
					}
				}
				callback.invoke(result)
				aliasCache.put(guild.id, result)
			}
		} else {
			callback.invoke(aliasCache[guild.id]!!)
		}
	}

	fun alreadyExists(guild: Guild, name: String, callback: (Boolean) -> Unit) {
		transaction {
			callback.invoke(Colors.select { Colors.guild_id eq guild.id }.any { it[Colors.name] == name })
		}
	}

	fun sendMessage(message: String, channel: TextChannel, sender: Member) {
		if (channel.canTalk()) {
			channel.sendMessage(message).queue()
		} else {
			val notify: String = "\n_You received this DM because I do not have permission to send messages._"
			if (!sender.user.hasPrivateChannel()) {
				sender.user.openPrivateChannel().queue({
					sender.user.privateChannel.sendMessage(message + notify).queue()
				})
			} else {
				sender.user.privateChannel.sendMessage(message + notify).queue()
			}
		}
	}

	fun sendMessage(message: MessageEmbed, channel: TextChannel, sender: Member) {
		if (channel.canTalk() && sender.guild.selfMember.hasPermission(Permission.MESSAGE_EMBED_LINKS)) {
			channel.sendMessage(message).queue()
		} else {
			if (!sender.user.hasPrivateChannel()) {
				sender.user.openPrivateChannel().queue({
					sender.user.privateChannel.sendMessage(message).queue()
				})
			} else {
				sender.user.privateChannel.sendMessage(message).queue()
			}
		}
	}
}