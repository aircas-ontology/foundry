package com.aircas.ptr.foundry.ontology.entity.objectType;



// 这里应该是动态生成的，目前还是写死的，这里需要动态生成

import com.aircas.ptr.foundry.ontology.entity.vo.ObjectValueVo;
import com.aircas.ptr.foundry.ontology.entity.vo.PropertyValueVO;

import java.util.List;

public class XTMB {
    public String mbbh;
    public String mbmc;
    public String zbxh;



    public XTMB createFromPlainObject(ObjectValueVo objectValueVo) {
        List<PropertyValueVO> properties = objectValueVo.getProperties();
        return null;
    }
}
