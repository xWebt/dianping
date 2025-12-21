package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IShopTypeService typeService;

    @Override
    public Result queryTypeByList() {
        String cacheKey = "shop:type:list";

        List<ShopType> list = null;
        String typeDate = stringRedisTemplate.opsForValue().get(cacheKey);

        if(typeDate != null) {
            list = JSONUtil.toList(typeDate, ShopType.class);
            return Result.ok(list);
        }

        list = typeService
                .query().orderByAsc("sort").list();

        if(list != null && !list.isEmpty()) {
            String string = JSONUtil.toJsonStr(list);
            stringRedisTemplate.opsForValue().set(cacheKey, string,30, TimeUnit.MINUTES);
        }

        return Result.ok(list);
    }
}
