/*
 * Copyright (C) 2020 Chewbotcca
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package pw.chew.chewbotcca.commands.info;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.menu.Paginator;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import pw.chew.chewbotcca.util.JDAUtilUtil;
import pw.chew.chewbotcca.util.Mention;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

// %^rinfo command
public class RoleInfoCommand extends Command {

    public RoleInfoCommand() {
        this.name = "roleinfo";
        this.aliases = new String[]{"rinfo"};
        this.botPermissions = new Permission[]{Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_ADD_REACTION};
        this.guildOnly = true;
    }

    @Override
    protected void execute(CommandEvent event) {
        // Get the args
        String arg = event.getArgs();
        if (arg.isEmpty()) {
            event.reply("Please specify a role name to look up!");
            return;
        }

        String mode = "";

        // Set the mode if necessary
        if(arg.contains("members")) {
            mode = "members";
            arg = arg.replace("--members", "").trim();
        }

        boolean mention = false;
        if(arg.contains("--mention")) {
            mention = true;
            arg = arg.replace("--mention", "").trim();
        }

        // Parse and find the role
        Role role = null;
        boolean id;
        try {
            Long.parseLong(arg);
            id = true;
        } catch (NumberFormatException e) {
            id = false;
        }
        if(arg.contains("<")) {
            role = (Role) Mention.parseMention(arg, event.getGuild(), event.getJDA());
        } else if(id) {
            role = event.getGuild().getRoleById(arg);
        } else {
            List<Role> roles = event.getGuild().getRolesByName(arg, true);
            if(roles.size() > 0) {
                role = roles.get(0);
            }
        }
        if(role == null) {
            event.reply("No roles found for the given input.");
            return;
        }

        // Make a response depending on the mode
        if(mode.equals("members")) {
            gatherMembersInfo(event, role, mention);
        } else {
            event.reply(gatherMainInfo(event, role).build());
        }
    }

    /**
     * Gather main role info
     * @param event the command event
     * @param role the role
     * @return an embed to be build
     */
    public EmbedBuilder gatherMainInfo(CommandEvent event, Role role) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Role Information for: " + role.getName());
        // Send typing, it'll be a while
        event.getChannel().sendTyping().queue();
        new Thread(() -> event.getGuild().loadMembers().get());
        // I hate async, so this puts it back in sync
        await().atMost(30, TimeUnit.SECONDS).until(() -> event.getGuild().getMemberCache().size() == event.getGuild().getMemberCount());
        // Get the member counts
        int members = event.getGuild().getMembersWithRoles(role).size();
        int total = event.getGuild().getMemberCount();
        DecimalFormat df = new DecimalFormat("#.##");
        String percent = df.format((float)members / (float)total * 100);
        // Return the member count
        embed.addField("Members", NumberFormat.getNumberInstance(Locale.US).format(members) + " / " + NumberFormat.getNumberInstance(Locale.US).format(total) + " (" + percent + "%)", true);
        embed.addField("Mention / ID", role.getAsMention() + "\n" + role.getId(), true);
        // Find and provide info
        List<String> info = new ArrayList<>();
        info.add(getInfoFormat(role.isHoisted(), "Hoisted"));
        info.add(getInfoFormat(role.isMentionable(), "Mentionable"));
        info.add(getInfoFormat(role.getTags().isBot(), "Bot Role"));
        info.add(getInfoFormat(role.getTags().isBoost(), "Boost Role"));
        info.add(getInfoFormat(role.getTags().isIntegration(), "Integration Role"));
        embed.addField("Information", String.join("\n", info), true);
        embed.setColor(role.getColor());
        embed.setFooter("Created");
        // If the user has permission to manage roles, show the permissions
        if(event.getMember().hasPermission(Permission.MANAGE_ROLES)) {
            Permission[] perms = role.getPermissions().toArray(new Permission[0]);
            Set<Permission> temp = new TreeSet<>(Arrays.asList(perms));
            String description = "";
            if (!role.isPublicRole()) {
                Permission[] every = event.getGuild().getPublicRole().getPermissions().toArray(new Permission[0]);
                temp.removeAll(Arrays.asList(every));
                description = "Elevated permissions (perms this role has that everyone role doesn't)\n\n";
            }
            embed.setDescription(description + generatePermissionList(temp));
        }
        embed.setTimestamp(role.getTimeCreated());

        return embed;
    }

    /**
     * Gather members of a role
     * @param event the command event
     * @param role the role
     * @param mention should render mentions or not
     */
    public void gatherMembersInfo(CommandEvent event, Role role, boolean mention) {
        if(role == event.getGuild().getPublicRole()) {
            event.reply("Finding all members is unsupported.");
            return;
        }
        Paginator.Builder paginator = JDAUtilUtil.makePaginator().clearItems();
        paginator.setText("Members in role " + role.getName());
        // Get members
        new Thread(() -> event.getGuild().loadMembers().get());
        // Put response back in sync
        await().atMost(30, TimeUnit.SECONDS).until(() -> event.getGuild().getMemberCache().size() == event.getGuild().getMemberCount());
        // Get the member list and find how members actually with the role
        List<Member> memberList = event.getGuild().getMemberCache().asList();
        for(Member member : memberList) {
            if(member.getRoles().contains(role)) {
                if (mention)
                    paginator.addItems(member.getAsMention());
                else
                    paginator.addItems(member.getUser().getAsTag());
            }
        }
        if (paginator.getItems().isEmpty())
            paginator.addItems("No one has this role!");

        Paginator p = paginator.setUsers(event.getAuthor()).build();

        p.paginate(event.getChannel(), 1);
    }

    /**
     * Generate a fancy permission list
     * @param permissions the permissions
     * @return a permission list
     */
    public String generatePermissionList(Set<Permission> permissions) {
        ArrayList<CharSequence> perms = new ArrayList<>();
        Permission[] permList = permissions.toArray(new Permission[0]);
        if (permissions.isEmpty()) {
            return "*No perms selected*";
        }
        for(int i = 0; i < permissions.size(); i++) {
            perms.add(permList[i].getName());
        }
        return String.join(", ", perms);
    }

    /**
     * Helper method for %^rinfo info formatting
     * @param yes if it's green or not
     * @param string the string to append
     * @return a formatted string
     */
    private String getInfoFormat(boolean yes, String string) {
        if (yes) {
            return "\uD83D\uDFE2 " + string;
        } else {
            return "\uD83D\uDD34 " + string;
        }
    }
}

