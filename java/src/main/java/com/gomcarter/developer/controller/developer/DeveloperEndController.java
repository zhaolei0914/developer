package com.gomcarter.developer.controller.developer;

import com.gomcarter.developer.dto.EndDto;
import com.gomcarter.developer.entity.End;
import com.gomcarter.developer.params.EndParam;
import com.gomcarter.developer.service.EndService;
import com.gomcarter.frameworks.base.common.AssertUtils;
import com.gomcarter.frameworks.base.exception.CustomException;
import com.gomcarter.frameworks.base.pager.DefaultPager;
import com.gomcarter.frameworks.interfaces.annotation.Notes;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author gomcarter
 * @date 2019-06-17 20:58:17
 */
@RestController
@RequestMapping("developer/end")
public class DeveloperEndController {

    @Resource
    EndService endService;

    @PostMapping(value = "", name = "新增前端项目")
    void list(@Notes("项目名称") @RequestParam String name,
              @Notes("对应前缀") @RequestParam String prefix,
              @Notes("登录使用的jar包地址") String jarUrl,
              @Notes("登录使用的类名") String kls,
              @Notes("登录使用的方法") String method,
              @Notes("登录使用的jar对应方法的参数: json字符串格式:[{\"key\":\"java.lang.Long\", \"value\": 6}], key是参数的类,value是对应的值") String args,
              @Notes("header值是什么") String header,
              @Notes("备注") String mark) throws Exception {

        End end = new End().setName(name)
                .setPrefix(prefix)
                .setJarUrl(jarUrl)
                .setKls(kls)
                .setMethod(method)
                .setArgs(args)
                .setHeader(header)
                .setMark(mark);

        validate(end);

        endService.insert(end);
    }

    private void validate(End end) throws Exception {
        if (StringUtils.isNotBlank(end.getJarUrl())) {
            Method m = EndService.getMethod(end);
            AssertUtils.isTrue(Modifier.isStatic(m.getModifiers()), new CustomException(m.getName() + "不是静态方法，哥！"));
        }
    }

    @PutMapping(value = "{id}", name = "修改前端项目")
    void list(@Notes("主键") @PathVariable("id") Long id,
              @Notes("项目名称") @RequestParam String name,
              @Notes("对应前缀") @RequestParam String prefix,
              @Notes("登录使用的jar包地址") String jarUrl,
              @Notes("登录使用的类名") String kls,
              @Notes("登录使用的方法") String method,
              @Notes("登录使用的jar对应方法的参数: json字符串格式:[{\"key\":\"java.lang.Long\", \"value\": 6}], key是参数的类,value是对应的值") String args,
              @Notes("header值是什么") String header,
              @Notes("备注") String mark) throws Exception {

        End end = new End().setId(id)
                .setName(name)
                .setPrefix(prefix)
                .setJarUrl(jarUrl)
                .setKls(kls)
                .setMethod(method)
                .setArgs(args)
                .setHeader(header)
                .setMark(mark);

        validate(end);

        endService.update(end);
    }

    @GetMapping(value = "{id}", name = "获取前端项目详情")
    EndDto get(@Notes("主键") @PathVariable("id") Long id) {
        return this.list(new EndParam().setId(id), new DefaultPager()).get(0);
    }

    @GetMapping(value = "list", name = "获取接口地址列表")
    List<EndDto> list(@Notes("查询参数") EndParam params, @Notes("分页器") DefaultPager pager) {
        return endService.query(params, pager)
                .stream()
                .map(s -> new EndDto()
                        .setId(s.getId())
                        .setName(s.getName())
                        .setPrefix(s.getPrefix())
                        .setJarUrl(s.getJarUrl())
                        .setKls(s.getKls())
                        .setMethod(s.getMethod())
                        .setArgs(s.getArgs())
                        .setHeader(s.getHeader())
                        .setMark(s.getMark())
                        .setCreateTime(s.getCreateTime())
                )
                .collect(Collectors.toList());
    }

    @GetMapping(value = "count", name = "获取接口地址列表总数")
    Integer count(@Notes("查询参数") EndParam params) {
        return endService.count(params);
    }
}
