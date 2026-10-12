package net.kasax.bobbot.work;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.bot.task.GoToTask;
import net.kasax.bobbot.bot.task.KillTask;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.Lang;
import net.kasax.bobbot.companion.job.Job;
import net.kasax.bobbot.companion.task.IdleTask;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;

/**
 * "Guard this place": at his post, and every monster that comes within the radius fought (none
 * chased further than that); back to the post after. Never a player, a pet or a villager.
 */
public final class GuardJob extends Job {
    private BlockPos post;
    private int radius = 16;
    private int kills;

    public GuardJob() {
    }

    public GuardJob(BlockPos post, int radius) {
        this.post = post.immutable();
        this.radius = radius;
    }

    @Override
    public String kind() {
        return "guard";
    }

    @Override
    public String describe(Lang l) {
        return l.t("guard " + post.toShortString(), post.toShortString() + " bewachen");
    }

    @Override
    public String progress(Lang l) {
        return l.t(kills + " monsters", kills + " Monster");
    }

    @Override
    public boolean endless() {
        return true;
    }

    @Override
    public BotTask next(Companion c, Bot bot) {
        var body = bot.body();
        Mob target = body.level().getEntitiesOfClass(Mob.class, new AABB(post).inflate(radius, 8, radius),
                        m -> m.isAlive() && m instanceof Enemy && !m.isInvisible() && m.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(post)) <= radius * radius)
                .stream().min(Comparator.comparingDouble(m -> m.distanceToSqr(body))).orElse(null);
        if (target != null) {
            kills++;
            return new KillTask(target).nearby(radius + 6);
        }
        if (body.blockPosition().distSqr(post) > 9) return new GoToTask(post, 1.5);
        return new IdleTask(30, c.ownerPlayer(bot.server()));
    }

    @Override
    protected void save(CompoundTag t) {
        t.putLong("post", post.asLong());
        t.putInt("radius", radius);
    }

    @Override
    protected void load(CompoundTag t) {
        post = BlockPos.of(t.getLongOr("post", 0));
        radius = t.getIntOr("radius", 16);
    }
}
