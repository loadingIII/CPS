package com.campus.repair.module.place;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.repair.common.BusinessException;
import com.campus.repair.module.place.dto.PlaceNodeVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlaceService {

    private final PlaceMapper placeMapper;

    public PlaceService(PlaceMapper placeMapper) {
        this.placeMapper = placeMapper;
    }

    /** 位置树（四级嵌套）；includeDisabled=false 时过滤禁用节点 */
    public List<PlaceNodeVO> tree(boolean includeDisabled) {
        List<Place> all = placeMapper.selectList(new LambdaQueryWrapper<Place>().orderByAsc(Place::getId));
        if (!includeDisabled) {
            all = all.stream().filter(p -> Boolean.TRUE.equals(p.getEnabled())).toList();
        }
        Map<Long, List<Place>> byParent = new LinkedHashMap<>();
        for (Place p : all) {
            byParent.computeIfAbsent(p.getParentId(), k -> new ArrayList<>()).add(p);
        }
        return buildChildren(null, byParent);
    }

    private List<PlaceNodeVO> buildChildren(Long parentId, Map<Long, List<Place>> byParent) {
        List<Place> nodes = byParent.getOrDefault(parentId, List.of());
        return nodes.stream()
                .sorted(Comparator.comparing(Place::getId))
                .map(p -> new PlaceNodeVO(p.getId(), p.getType(), p.getName(), p.getCode(),
                        p.getEnabled(), buildChildren(p.getId(), byParent)))
                .toList();
    }

    @Transactional
    public Place create(PlaceType type, Long parentId, String name, String code) {
        if (StringUtils.hasText(code) && code.length() > 64) {
            throw BusinessException.param("编码过长");
        }
        Place p = new Place();
        p.setType(type);
        p.setName(name);
        p.setCode(code);
        p.setEnabled(true);
        if (type == PlaceType.CAMPUS) {
            if (parentId != null) {
                throw BusinessException.param("校区不能有父节点");
            }
        } else {
            if (parentId == null) {
                throw BusinessException.param(type + " 必须选择父节点");
            }
            Place parent = placeMapper.selectById(parentId);
            if (parent == null || !Boolean.TRUE.equals(parent.getEnabled())) {
                throw BusinessException.param("父节点不存在或已禁用");
            }
            if (parent.getType() != type.parentType()) {
                throw BusinessException.param("父节点类型必须是 " + type.parentType());
            }
            p.setParentId(parentId);
            p.setCampusId(rootCampusId(parent));
        }
        placeMapper.insert(p);
        return p;
    }

    @Transactional
    public void update(Long id, String name, String code, Boolean enabled) {
        Place p = placeMapper.selectById(id);
        if (p == null) {
            throw BusinessException.notFound("位置不存在");
        }
        if (enabled != null && !enabled && Boolean.TRUE.equals(p.getEnabled())) {
            Long childCount = placeMapper.selectCount(new LambdaQueryWrapper<Place>()
                    .eq(Place::getParentId, id).eq(Place::getEnabled, true));
            if (childCount != null && childCount > 0) {
                throw BusinessException.param("存在启用的下级位置，不能禁用");
            }
        }
        if (name != null) {
            p.setName(name);
        }
        if (code != null) {
            p.setCode(code);
        }
        if (enabled != null) {
            p.setEnabled(enabled);
        }
        placeMapper.updateById(p);
    }

    /** 沿父链向上取根校区 id */
    public Long rootCampusId(Place node) {
        Place cur = node;
        int depth = 0;
        while (cur.getParentId() != null && depth++ < 10) {
            cur = placeMapper.selectById(cur.getParentId());
            if (cur == null) {
                break;
            }
        }
        return cur != null && cur.getType() == PlaceType.CAMPUS ? cur.getId() : null;
    }

    /** 位置全路径串，如「东校区/教学区/一教/101」 */
    public String buildPath(Long placeId) {
        Deque<String> names = new ArrayDeque<>();
        Place cur = placeMapper.selectById(placeId);
        int depth = 0;
        while (cur != null && depth++ < 10) {
            names.push(cur.getName());
            cur = cur.getParentId() == null ? null : placeMapper.selectById(cur.getParentId());
        }
        return String.join("/", names);
    }

    /** 位置自身 + 全部祖先 id（派单范围匹配用） */
    public List<Long> ancestorIds(Long placeId) {
        List<Long> ids = new ArrayList<>();
        Place cur = placeMapper.selectById(placeId);
        int depth = 0;
        while (cur != null && depth++ < 10) {
            ids.add(cur.getId());
            cur = cur.getParentId() == null ? null : placeMapper.selectById(cur.getParentId());
        }
        return ids;
    }

    /**
     * 位置坐标（导航用）：沿父链向上回退，取最近一级有坐标的节点（房间无坐标则用房栋/片区）。
     * 返回 {lng, lat}，全链无坐标返回 null。
     */
    public Map<String, Object> findGeoWithFallback(Long placeId) {
        Place cur = placeMapper.selectById(placeId);
        int depth = 0;
        while (cur != null && depth++ < 10) {
            Map<String, Object> geo = placeMapper.selectGeoById(cur.getId());
            if (geo != null && geo.get("lng") != null && geo.get("lat") != null) {
                return geo;
            }
            cur = cur.getParentId() == null ? null : placeMapper.selectById(cur.getParentId());
        }
        return null;
    }
}
