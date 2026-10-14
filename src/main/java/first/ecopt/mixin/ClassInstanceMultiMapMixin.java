package first.ecopt.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import first.ecopt.duck.AllInstancesHolder;
import net.minecraft.util.ClassInstanceMultiMap;

/**
 * 去掉 1.21.1 里 {@code ClassInstanceMultiMap} 的迭代包装：
 *
 * <pre>
 * // 原版
 * public Iterator&lt;T&gt; iterator() {
 *     return this.allInstances.isEmpty() ? Collections.emptyIterator()
 *          : Iterators.unmodifiableIterator(this.allInstances.iterator());   // ← 火焰图里的 Iterators$1
 * }
 * public &lt;S&gt; Collection&lt;S&gt; find(Class&lt;S&gt; type) { ... return Collections.unmodifiableCollection(list); }
 * </pre>
 *
 * 包装类的每次 hasNext()/next() 都是一次额外的虚调用，实体段查询每个元素都要走一遍。
 * 注入后直接返回底层 ArrayList 的迭代器；调用方（EntitySection / 其它原版代码）只做读取。
 */
@Mixin(ClassInstanceMultiMap.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class ClassInstanceMultiMapMixin implements AllInstancesHolder {

    @Shadow
    @Final
    private List allInstances;

    @Shadow
    @Final
    private Map byClass;

    @Shadow
    @Final
    private Class baseClass;

    @Override
    public List<?> ecopt$allInstances() {
        return this.allInstances;
    }

    @Inject(method = "iterator", at = @At("HEAD"), cancellable = true)
    private void ecopt$fastIterator(CallbackInfoReturnable<Iterator> cir) {
        List instances = this.allInstances;
        cir.setReturnValue(instances.isEmpty() ? Collections.emptyIterator() : instances.iterator());
    }

    @Inject(method = "find", at = @At("HEAD"), cancellable = true)
    private void ecopt$fastFind(Class type, CallbackInfoReturnable cir) {
        if (this.baseClass.isAssignableFrom(type)) {
            Map map = this.byClass;
            List cached = (List) map.get(type);
            if (cached == null) {
                List instances = this.allInstances;
                int size = instances.size();
                cached = new ArrayList(Math.max(4, size >> 2));
                for (int i = 0; i < size; i++) {
                    Object instance = instances.get(i);
                    if (type.isInstance(instance)) {
                        cached.add(instance);
                    }
                }
                map.put(type, cached);
            }
            cir.setReturnValue(cached);
        }
    }
}
