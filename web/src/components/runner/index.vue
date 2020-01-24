<template>
  <div>
    <el-form label-width="3em">
      <el-form-item label="环境：" style="width: 250px">
        <el-select v-model="env">
          <el-option label="开发" value="devDomain"></el-option>
          <el-option label="测试" value="testDomain"></el-option>
          <el-option label="预发" value="prevDomain"></el-option>
          <el-option label="线上" value="onlineDomain"></el-option>
        </el-select>
      </el-form-item>
    </el-form>
    <div>
      <span class="simple waiting"></span><span>-等待执行</span>&nbsp;&nbsp;
      <span class="simple running"></span><span>-执行中</span>&nbsp;&nbsp;
      <span class="simple success"></span><span>-执行成功</span>&nbsp;&nbsp;
      <span class="simple failed"></span><span>-执行失败</span>
    </div>
    <el-container>
      <el-main class="container" :style="{width: width * 0.6 + 'px', height: height + 'px'}">
        <div class="flow-container"></div>
      </el-main>
      <el-aside :style="{width: width * 0.4 + 'px', height: height + 'px'}" class="runner-log">
        <div v-for="(log, index) of logs" :key="index" style="min-height: 20px;">
          <span v-if="log.type === 'json'">
            <v-jsonformatter :json="log.data" :min-height="25"></v-jsonformatter>
          </span>
          <span v-else>{{ log.data }}</span>
        </div>
      </el-aside>
    </el-container>
  </div>
</template>

<script>
import G6 from '@antv/g6'
import { xhr } from '@/config/api/http'
import { functionListApi } from '@/config/api/inserv-api'
import { toQueryString } from '@/config/utils'

export default {
  props: {
    width: {
      type: Number,
      default: 1000
    },
    height: {
      type: Number,
      default: 618
    }
  },
  data () {
    return {
      logs: [],
      env: 'testDomain',
      workflow: {
        nodes: null,
        edges: null
      },
      presetParams: null,
      model: []
    }
  },
  methods: {
    setData (data) {
      this.log('========开始========')
      this.workflow = data.workflow
      this.presetParams = data.presetParams || []

      // render
      this.graph.data(this.workflow)
      this.graph.render()

      this.constructExecutableDataModel()

      // 初始化预制参数
      this.initPresetParams()
    },
    /**
     * 对节点进行编排，编排成这样 [[node1,node2 ...], [node3,node4...]] 将node分层，方便依次执行这些节点。
     */
    constructExecutableDataModel () {
      this.log('初始化运行流程：')
      this.model = []
      // 遍历节点找到最顶端的节点设置为level 1， 往下一层level 2， 以此类推，把所有节点分层
      // 然后最后执行时就从第一层开始执行，到最后一层。
      const nodes = this.graph.getNodes()
      nodes.forEach(n => { n.getModel().mark = 0 })
      const totalCounts = nodes.length

      // 第一层怎么找: 存在任何一个节点只有出没有入的节点
      let currentNodes = nodes.filter(n => n.getEdges().filter(e => e.getSource() !== n).length === 0)
      let count = currentNodes.length
      if (count === 0) {
        // 如果没有找到首节点，证明产生了回环，那么随便选一个节点作为 currentNodes
        currentNodes = [nodes[0]]
        count++
      }
      this.model.push(currentNodes)
      // 标记节点已经被修改
      currentNodes.forEach(n => { n.getModel().mark = n.getModel().mark + 1 })
      currentNodes.forEach(n => console.log(this.model.length + '：', n.getModel().id, n.getModel().label))

      // 第二层开始：入口是上一层的下层节点，而且（如果一个节点存在多个入口，如又是第二层又是第三层，那么要取第三层）
      while (count < totalCounts) {
        currentNodes = this.graph.getEdges()
          .filter(e => {
            const isPreLevelChildren = currentNodes.indexOf(e.getSource()) >= 0
            if (isPreLevelChildren) {
              const target = e.getTarget()
              const targetIsMe = target.getEdges().filter(n => n.getTarget() === target)
              target.getModel().mark = target.getModel().mark + 1
              if (targetIsMe.length === target.getModel().mark) {
                return true
              }
            }
            return false
          })
          .map(e => e.getTarget())

        if (currentNodes.length === 0) {
          // 没有找到则从剩下的node中随便取一个
          currentNodes = [nodes.filter(n => n.getModel().mark === false)[0]]
          count++
        } else {
          count += currentNodes.length
        }

        // 表示已处理
        this.model.push(currentNodes)
      }

      // logs
      let index = 1
      this.model.forEach(m => {
        this.log(index + ' => ' + m.map(mm => `${mm.getModel().label}（$${mm.getModel().id}）`).join('，'))
        index++
      })
      this.log('')
    },
    registerEdge () {
      // lineDash 的差值，可以在后面提供 util 方法自动计算
      const dashArray = [
        [ 0, 1 ],
        [ 0, 2 ],
        [ 1, 2 ],
        [ 0, 1, 1, 2 ],
        [ 0, 2, 1, 2 ],
        [ 1, 2, 1, 2 ],
        [ 2, 2, 1, 2 ],
        [ 3, 2, 1, 2 ],
        [ 4, 2, 1, 2 ]
      ]
      const lineDash = [ 4, 2, 1, 2 ]
      const interval = 9
      G6.registerEdge('running-line', {
        setState (name, value, item) {
          const shape = item.get('keyShape')
          if (name === 'running') {
            if (value) {
              // 后续 G 增加 totalLength 的接口
              const length = shape.getTotalLength()
              let totalArray = []
              for (let i = 0; i < length; i += interval) {
                totalArray = totalArray.concat(lineDash)
              }
              let index = 0
              shape.animate({
                onFrame () {
                  const cfg = {
                    lineDash: dashArray[index].concat(totalArray)
                  }
                  index = (index + 1) % interval
                  return cfg
                },
                repeat: true
              }, 1)
            } else {
              shape.stopAnimate()
              shape.attr('lineDash', null)
            }
          } else if (name === 'selected') {
            if (value) {
              shape.attr('shadowBlur', 5)
            } else {
              shape.attr('shadowBlur', 0)
            }
          }
        }
      }, 'line')
    },
    initGraph () {
      // 创建graph
      this.graph = new G6.Graph({
        container: this.$el.querySelectorAll('.flow-container')[0],
        width: this.width * 0.6,
        height: this.height,
        // 是否开启画布自适应。开启后图自动适配画布大小。
        // fitView: true,
        // fitViewPadding: 100,
        modes: {
          default: ['drag-node', 'drag-canvas', 'zoom-canvas']
        },
        nodeStateStyles: {
          waiting: {
            radius: 4,
            fill: 'yellow',
            stroke: '#4063ff'
          },
          running: {
            radius: 4,
            fill: 'yellowgreen',
            stroke: '#4063ff'
          },
          success: {
            radius: 4,
            fill: 'green',
            stroke: '#4063ff'
          },
          failed: {
            radius: 4,
            fill: 'red',
            stroke: '#4063ff'
          }
        },
        defaultNode: {
          shape: 'rect',
          size: [120, 50],
          label: '新增节点',
          style: {
            radius: 4,
            fill: '#fff',
            stroke: '#4063ff'
          }
        },
        defaultEdge: {
          shape: 'running-line',
          style: {
            stroke: '#4063ff',
            shadowColor: 'black',
            endArrow: {
              path: 'M 6,0 L -6,-6 L -6,6 Z',
              d: 6
            },
            lineAppendWidth: 20
          }
        }
      })
    },
    initPresetParams () {
      // 1,拿出固定值
      this.log('初始化预置参数：')
      this.presetParams.filter(s => s.fix)
        .forEach(s => {
          window['$' + s.key] = s.value
          this.log('$' + s.key + ' => ' + s.value)
        })

      // 2，拿出非固定值，请求获取到function，再通过function获取到最新的值
      const functionPresetParams = this.presetParams.filter(s => !s.fix)
      const functionIdList = functionPresetParams.map(s => s.functionId)
      functionListApi({idList: functionIdList})
        .then(r => {
          const map = {}
          r.forEach(c => {
            /* eslint-disable */
            map[c.id] = Function(c.script)()
          })

          functionPresetParams.forEach(s => {
            window['$' + s.key] = map[s.functionId]
            this.log('$' + s.key + ' => ' + window['$' + s.key])
          })
          this.log('')
        })
    },
    clearState (node) {
      node = node == null ? this.graph.getNodes() : (node instanceof Array ? node : [node])

      node.forEach(n => {
        this.graph.setItemState(n, 'waiting', false)
        this.graph.setItemState(n, 'running', false)
        this.graph.setItemState(n, 'success', false)
        this.graph.setItemState(n, 'failed', false)
        this.graph.setItemState(n, 'selected', false)
      })
    },
    setState (node, state, bool = true) {
      node = node == null ? this.graph.getNodes() : (node instanceof Array ? node : [node])
      state = state instanceof Array ? state : [state]
      node.forEach(n => state.forEach(s => this.graph.setItemState(n, s, bool)))
    },
    run () {
      this.log('开始执行：')
      this.clearState()
      this.setState(null, 'waiting')
      this.runLevel(0)
    },
    runLevel (level) {
      console.log('now level ' + level)

      if (level >= this.model.length) {
        return
      }

      const toBeRunning = this.model[level]
      const totalCount = toBeRunning.length
      let count = 0
      let error = false

      toBeRunning.forEach(node => {
        const edges = node.getEdges().filter(e => e.getSource() === node)
        this.setState(edges.concat(node), ['selected', 'running'])

        const model = node.getModel()
        const data = model.data
        this.log(`开始执行节点：${model.label}（$${model.id}）`)
        this.buildXhr(data)
          .then((d) => {
            console.log('success', d)
            // 赋值
            window['$' + model.id] = d.data.extra
            this.log(`返回结果：`)
            this.log(window['$' + model.id], 'json')
            if (data.javascript) {
              // 有脚本，则按照脚本重新赋值
              window['$' + model.id] = new Function(data.javascript)
              this.log(`脚本处理后结果：`)
              this.log(window['$' + model.id], 'json')
            }

            // 将结果放到输送线上去
            edges.forEach(e => { window['$' + e.getModel().id] = window['$' + model.id] })

            this.setState(edges.concat(node), ['selected', 'running'], false)
            this.setState(node, ['success'], true)

            this.log('')
          })
          .catch((e) => {
            this.log('执行出错了' + e.toString())

            this.setState(edges.concat(node), ['selected', 'running'], false)
            this.setState(node, ['failed'], true)
            error = true
          })

        count++
      })

      // 等待所有上述执行完毕，进行下次操作
      const timer = setInterval(() => {
        if (error) {
          clearInterval(timer)
        }

        if (totalCount <= count) {
          this.runLevel(level + 1)
          clearInterval(timer)
        }
      }, 3000)
    },
    buildXhr (model) {
      let params
      let method = model.method.toLowerCase()
      let res
      let obj
      let url = model.java[this.env] + model.url
      let bodyParams
      if (model.parameters.length === 0) {
        obj = ''
      } else {
        obj = {}
        model.parameters.forEach(e => {
          let value
          try {
            value = new Function('return ' + e['defaults'])()
          } catch (e) {
            value = e['defaults']
          }

          if (e.inputType === 'textarea') {
            bodyParams = value
          } else if (url.indexOf(`{${e['key']}}`) >= 0) {
            url = url.replace(`{${e['key']}}`, value)
          } else {
            // 同一key的时候，需要合并
            if (obj[e['key']] === undefined) {
              obj[e['key']] = value
            } else {
              obj[e['key']] = [value].concat(obj[e['key']])
            }
          }
        })
      }

      if (['put', 'post', 'patch'].indexOf(method) >= 0) {
        params = obj
      } else { // get and delete
        params = {
          params: obj
        }
      }

      console.log(params)

      const headers = model.headers.map(h => {
        let value
        try {
          value = new Function('return ' + h.value)()
        } catch (e) {
          value = h.value
        }
        return {key: h.key, value: value}
      })

      if (bodyParams) {
        let body
        let type
        try {
          body = JSON.stringify(JSON.parse(bodyParams))
          type = 'json'
        } catch (e) {
          body = bodyParams
          type = 'raw'
        }
        delete obj[bodyParams]

        const u = url + (url.indexOf('?') >= 0 ? '&' : '?') + toQueryString(obj)
        this.log(`${method.toUpperCase()} ${url}`)
        headers.length && this.log(`header：${headers.map(h => h.key + '=' + h.value).join('，')}`)
        this.log(`body：${body}`)
        res = xhr[method](u, body, {notice: false, type: type, customHeaders: headers})
      } else {
        this.log(`${method.toUpperCase()} ${url}`)
        headers.length && this.log(`header：${headers.map(h => h.key + '=' + h.value).join('，')}`)
        this.log(`参数：${JSON.stringify(obj)}`)
        params.customHeaders = headers
        res = xhr[method](url, params, {notice: false})
      }
      return res
    },
    log (log, type = 'text') {
      this.logs.push({data: log, type: type})
      // 滚动条滚到最下面
      setTimeout(() => {
        const container = this.$el.querySelector(".runner-log")
        container.scrollTop = container.scrollHeight
      }, 1)
    }
  },
  mounted () {
    // registerEdge
    this.registerEdge()

    // initGraph
    this.initGraph()

    window.that = this
  },
  components: {
    'v-jsonformatter': () => import('@/components/jsonformatter')
  }
}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style type="text/css" lang="scss" scoped>
  @import 'index';
</style>