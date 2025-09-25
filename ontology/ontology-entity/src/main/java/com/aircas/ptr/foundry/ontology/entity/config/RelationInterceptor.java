package com.aircas.ptr.foundry.ontology.entity.config;

import com.aircas.ptr.foundry.ontology.entity.model.document.OntologyRelation;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RelationInterceptor {

    @Before("execution(* com.aircas.ptr.foundry.ontology.entity.repository.arangodb.OntologyRelationRepository.save(..)) || " +
            "execution(* com.aircas.ptr.foundry.ontology.entity.service.*.createRelation(..))")
    public void validateRelation(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0 && args[0] instanceof OntologyRelation) {
            OntologyRelation relation = (OntologyRelation) args[0];
            
            // 确保_from和_to字段不为null
            if (relation.get_from() == null || relation.get_to() == null) {
                throw new IllegalArgumentException("关系的_from和_to字段不能为空");
            }
            
            // 确保_from和_to字段包含正确的集合前缀
            if (!relation.get_from().startsWith("nodes/") && !relation.get_from().contains("/")) {
                relation.set_from("nodes/" + relation.get_from());
            }
            
            if (!relation.get_to().startsWith("nodes/") && !relation.get_to().contains("/")) {
                relation.set_to("nodes/" + relation.get_to());
            }
        }
    }
}