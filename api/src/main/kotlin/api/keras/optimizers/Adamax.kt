package api.keras.optimizers

import api.core.KGraph
import api.keras.util.defaultAssignOpName
import api.keras.util.defaultInitializerOpName
import api.keras.util.defaultOptimizerVariableName
import org.tensorflow.Operand
import org.tensorflow.Output
import org.tensorflow.Shape
import org.tensorflow.op.Ops
import org.tensorflow.op.Scope
import org.tensorflow.op.core.Assign
import org.tensorflow.op.core.Constant
import org.tensorflow.op.core.Gradients
import org.tensorflow.op.core.Variable
import org.tensorflow.op.train.ApplyAdaMax
import java.util.*

private const val FIRST_MOMENT = "m"
private const val SECOND_MOMENT = "v"
private val FIRST_BETA_POWER_NAME = defaultOptimizerVariableName("beta1_power")

/**
 * Note: This optimizers works on CPU only. It has known bug on GPU: NaN instead of gradient values https://github.com/tensorflow/tensorflow/issues/26256
 */
class Adamax(
    private val learningRate: Float = 0.001f,
    private val beta1: Float = 0.9f,
    private val beta2: Float = 0.999f,
    private val epsilon: Float = 1e-07f,
    clipGradient: ClipGradientAction = NoClipGradient()
) : Optimizer(clipGradient) {

    private lateinit var epsilonConstant: Constant<Float>
    private lateinit var learningRateConst: Constant<Float>
    private lateinit var betaOneConst: Constant<Float>
    private lateinit var betaTwoConst: Constant<Float>
    private lateinit var betaOnePower: Variable<Float>

    override fun applyGradients(
        graph: KGraph,
        tf: Ops,
        weights: List<Variable<Float>>,
        gradients: Gradients
    ): List<Operand<Float>> {
        val targets: MutableList<Operand<Float>> =
            ArrayList()

        betaOneConst = tf.constant(beta1, getDType())
        betaTwoConst = tf.constant(beta2, getDType())
        learningRateConst = tf.constant(learningRate, getDType())
        epsilonConstant = tf.constant(epsilon, getDType())

        val scope = Scope(graph.tfGraph)

        for (i in weights.indices) {
            val variable = weights[i]
            val varName = variable.ref().op().name()

            val firstMomentSlot: Variable<Float> = getSlot(varName, FIRST_MOMENT)
            val secondMomentSlot: Variable<Float> = getSlot(varName, SECOND_MOMENT)

            targets.add(
                ApplyAdaMax.create(
                    scope,
                    variable,
                    firstMomentSlot,
                    secondMomentSlot,
                    betaOnePower,
                    learningRateConst,
                    betaOneConst,
                    betaTwoConst,
                    epsilonConstant,
                    clipGradient.clipGradient(tf, gradients.dy(i))
                )
            )
        }

        val betaOnePowerInit = tf
            .assign(betaOnePower, tf.math.mul(betaOnePower, betaOneConst))

        graph.addOptimizerVariableInitializer(betaOnePowerInit)
        graph.addOptimizerVariable(betaOnePower)

        return targets
    }

    private fun createAdamaxSlot(graph: KGraph, tf: Ops, v: Output<Float>) {
        val firstMomentInitializerName = defaultInitializerOpName(createName(v, FIRST_MOMENT))
        val firstMomentInitializer =
            tf.withName(firstMomentInitializerName).fill(tf.shape(v), tf.constant(0.0f, getDType()))
        createSlot(graph, tf, v.asOutput(), FIRST_MOMENT, firstMomentInitializer)

        val secondMomentInitializerName = defaultInitializerOpName(createName(v, SECOND_MOMENT))
        val secondMomentInitializer = tf.withName(secondMomentInitializerName)
            .fill(tf.shape(v), tf.constant(0.0f, getDType()))
        createSlot(graph, tf, v.asOutput(), SECOND_MOMENT, secondMomentInitializer)
    }

    override fun createSlots(graph: KGraph, tf: Ops, variables: List<Output<Float>>) {
        for (v in variables) {
            createAdamaxSlot(graph, tf, v.asOutput())
        }
        betaOnePower = tf.withName(FIRST_BETA_POWER_NAME).variable(Shape.scalar(), getDType())
        val betaOnePowerAssignName = defaultAssignOpName(FIRST_BETA_POWER_NAME)

        val betaOnePowerInit: Assign<*> = tf.withName(betaOnePowerAssignName)
            .assign(
                betaOnePower,
                tf.withName(defaultInitializerOpName(FIRST_BETA_POWER_NAME)).constant(beta1, getDType())
            )
        graph.addOptimizerVariableInitializer(betaOnePowerInit)
    }

    override fun getOptimizerName(): String {
        return "Adamax"
    }
}