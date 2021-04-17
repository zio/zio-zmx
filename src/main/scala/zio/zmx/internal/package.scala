package zio.zmx

import java.util.concurrent.atomic.AtomicReferenceArray

package object internal {

  def atomicArraytoArray(atomicArray: AtomicReferenceArray[Double]): Array[Double] = {
    val length = atomicArray.length
    val array  = Array.ofDim[Double](length)
    var i      = 0
    while (i < length) {
      array(i) = atomicArray.get(i)
      i += 1
    }
    array
  }
}
