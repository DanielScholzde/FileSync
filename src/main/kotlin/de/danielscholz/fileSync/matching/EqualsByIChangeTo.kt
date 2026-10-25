package de.danielscholz.fileSync.matching


//fun <R> equalsByTo(mode: EnumSet<MatchMode>, ignoreDuplicatesOnIntersect: Boolean = false, block: EqualsBy<IChange<*, File2>>.() -> R): R {
//
//    val equalsBy = EqualsBy(ignoreDuplicatesOnIntersect, object : EqualsAndHashCodeSupplier<IChange<*, File2>> {
//
//        val eq = EqualsAndHashCodeSupplierImpl(mode)
//
//        override fun equals(obj1: IChange<*, File2>, obj2: IChange<*, File2>): Boolean {
//            return eq.equals(obj1.to, obj2.to)
//        }
//
//        override fun hashCode(obj: IChange<*, File2>): Int {
//            return eq.hashCode(obj.to)
//        }
//    })
//
//    return equalsBy.block()
//}