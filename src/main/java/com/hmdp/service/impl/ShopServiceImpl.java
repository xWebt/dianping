package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result queryById(Long id) {
        //缓存穿透
        // Shop shop = queryWithPassThrough(id)

        //互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    public Shop queryWithMutex(Long id) {
        String  key  = RedisConstants.CACHE_SHOP_KEY + id;
        String string = stringRedisTemplate.opsForValue().get(key);

        if(StrUtil.isNotBlank(string)){
            return JSONUtil.toBean(string, Shop.class);
        }

        if(string == null){
            //isNotBlank在null情况下也是false，防止再查数据库
            return null;
        }

        //实现缓存重建


        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);

            if (!isLock) {
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //查数据库
             shop = getById(id);

            if (shop == null) {
                //写空值避免缓存穿透
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);

                return null;
            }

            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        }catch(InterruptedException e){
            throw new RuntimeException(e);
        }
        finally{
            //释放锁
            unlock(lockKey);
        }
        return shop;
    }

    public Shop queryWithPassThrough(Long id) {
        String  key  = RedisConstants.CACHE_SHOP_KEY + id;
        String string = stringRedisTemplate.opsForValue().get(key);

        if(StrUtil.isNotBlank(string)){
            return JSONUtil.toBean(string, Shop.class);
        }

        if(string == null){
            //isNotBlank在null情况下也是false，防止再查数据库
            return null;
        }

        //查数据库
        Shop byId = getById(id);

        if(byId == null){
            //写空值避免缓存穿透
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);

            return null;
        }
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(byId),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return byId;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result updateShop(Shop shop) {
        Long id = shop.getId();

        if(id == null){
            return Result.fail("店铺信息为空");
        }
        // 写入数据库
        updateById(shop);

        String  key  = RedisConstants.CACHE_SHOP_KEY + shop.getId();
        stringRedisTemplate.delete(key);

        return Result.ok();
    }
}
