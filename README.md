# ATC 4D Trajectory Prediction System

航空管制四维航迹预测系统 - 基于航班飞行计划和实时气象数据，预测飞机在未来时刻的空间四维坐标（经度、纬度、高度、时间）。

## 系统架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                        ATC 4D Trajectory System                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────┐    WebSocket/REST    ┌─────────────────────────┐  │
│  │   Frontend   │ ◀──────────────────▶ │   Backend (Spring Boot) │  │
│  │  React +     │   Trajectory Stream  │  ┌───────────────────┐  │  │
│  │  Cesium.js   │                      │  │ BADA Parser       │  │  │
│  │  3D Visual   │                      │  ├───────────────────┤  │  │
│  └──────────────┘                      │  │ Runge-Kutta 4     │  │  │
│                                        │  │  Trajectory Calc  │  │  │
│                                        │  ├───────────────────┤  │  │
│                                        │  │ Weather Interpol. │  │  │
│                                        │  └───────────────────┘  │  │
│                                        │            │             │  │
│                                        │            ▼             │  │
│                                        │  ┌───────────────────┐  │  │
│                                        │  │ PostgreSQL        │  │  │
│                                        │  └───────────────────┘  │  │
│                                        └─────────────────────────┘  │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

## 技术栈

### 后端
- **Java 17** + **Spring Boot 3.2.0**
- **PostgreSQL** + **Spring Data JPA**
- **STOMP over WebSocket** 实时推送
- **Apache Commons Math3** 数值计算
- **Apache Commons CSV** BADA文件解析
- **Lombok** 简化代码

### 前端
- **React 18** + **Vite 5**
- **Cesium.js** + **Resium** 3D地球可视化
- **Tailwind CSS 3** 样式框架
- **STOMP.js + SockJS** WebSocket客户端
- **Recharts** 数据可视化
- **Lucide React** 图标库

## 核心模块

### 1. BADA 动力学文件解析模块
- 解析欧洲空管局BADA（Base of Aircraft Data）CSV格式文件
- 提取飞机空气动力学参数：
  - 阻力极曲线（Drag Polar）
  - 推力模型（Thrust Model）
  - 燃油流量模型（Fuel Flow Model）
  - 爬升/巡航/下降性能参数
- 支持动态加载不同机型性能数据

### 2. 4D航迹计算模块
- **四阶龙格-库塔法（RK4）** 数值积分
- 5秒固定步长积分，支持自适应步长
- 完整飞行动力学计算：
  - 升力、阻力、推力计算
  - 爬升率/下降率计算
  - 燃油消耗计算
  - 质量变化跟踪
- **风温场插值**：三维网格点反距离加权（IDW）插值
- 考虑高空风对空速/地速转换的影响
- 航路点导航逻辑：大圆距离、方位角计算

### 3. WebSocket实时推送模块
- **STOMP** 协议消息代理
- 航迹点实时流式推送（按航班ID订阅）
- 飞行状态更新通知
- 订阅管理和流控制

### 4. Cesium.js 3D可视化模块
- OpenStreetMap 影像图层
- 航迹线渲染（历史轨迹 + 实时尾迹）
- 飞机3D模型渲染（带方向朝向）
- 飞行阶段颜色区分：
  - 🟠 起飞（Takeoff）
  - 🟢 爬升（Climb）
  - 🔵 巡航（Cruise）
  - 🟡 下降（Descent）
  - 🔴 着陆（Landing）
- 航路点标注
- 信息面板和状态监控

## 项目目录结构

```
04-atc-trajectory-4d/
├── backend/                          # Spring Boot 后端
│   ├── pom.xml                      # Maven 配置
│   └── src/main/java/com/atc/trajectory4d/
│       ├── bada/                     # BADA解析模块
│       │   ├── BadaFileParser.java
│       │   ├── BadaService.java
│       │   └── model/
│       │       └── AircraftPerformance.java
│       ├── trajectory/              # 航迹计算模块
│       │   ├── FlightStateVector.java
│       │   ├── RungeKuttaIntegrator.java
│       │   └── TrajectoryCalculator.java
│       ├── weather/                 # 气象模块
│       │   ├── WeatherData.java
│       │   └── WeatherService.java
│       ├── websocket/               # WebSocket模块
│       │   ├── WebSocketConfig.java
│       │   ├── WebSocketService.java
│       │   └── WebSocketController.java
│       ├── model/                   # 数据模型
│       │   ├── Waypoint.java
│       │   ├── FlightPlan.java
│       │   ├── TrajectoryPoint4D.java
│       │   ├── entity/               # JPA实体
│       │   └── dto/                  # DTO
│       ├── repository/              # Repository层
│       ├── service/                 # 业务服务层
│       ├── controller/              # REST控制器
│       └── config/                  # 配置类
│
├── frontend/                         # React 前端
│   ├── package.json
│   ├── vite.config.js
│   ├── tailwind.config.js
│   └── src/
│       ├── components/               # React组件
│       │   ├── CesiumGlobe.jsx      # 3D地球组件
│       │   ├── FlightList.jsx       # 航班列表
│       │   ├── FlightInfoPanel.jsx  # 航班信息面板
│       │   ├── TrajectoryChart.jsx  # 航迹图表
│       │   └── NotificationToast.jsx
│       ├── services/                 # 服务层
│       │   ├── api.js               # REST API
│       │   └── websocket.js         # WebSocket服务
│       ├── utils/                    # 工具函数
│       ├── App.jsx                  # 主应用组件
│       ├── main.jsx                 # 入口文件
│       └── index.css                # 全局样式
│
├── data/                             # 示例数据
│   ├── bada/                         # BADA性能数据
│   │   ├── B737-800.csv
│   │   └── A320.csv
│   ├── flightplans/                  # 飞行计划
│   │   ├── MU5101.json              # 北京→上海
│   │   └── CA1352.json              # 广州→北京
│   └── weather/                      # 气象数据
│
└── database/                         # 数据库脚本
    └── init.sql                     # 初始化脚本
```

## 数据模型

### TrajectoryPoint4D (4D航迹点)
| 字段 | 类型 | 描述 |
|------|------|------|
| longitude | double | 经度 (度) |
| latitude | double | 纬度 (度) |
| altitude | double | 高度 (米) |
| timestamp | Instant | 时间戳 |
| trueAirspeed | double | 真空速 (m/s) |
| groundSpeed | double | 地速 (m/s) |
| machNumber | double | 马赫数 |
| heading | double | 航向 (度) |
| verticalSpeed | double | 垂直速度 (m/s) |
| mass | double | 飞机质量 (kg) |
| fuelMass | double | 燃油质量 (kg) |
| thrust | double | 推力 (N) |
| drag | double | 阻力 (N) |
| lift | double | 升力 (N) |
| fuelFlow | double | 燃油流量 (kg/s) |
| windSpeed | double | 风速 (m/s) |
| windDirection | double | 风向 (度) |
| temperature | double | 温度 (°C) |
| flightPhase | Enum | 飞行阶段 |

### BADA 性能参数
- **气动参数**：CD0, CD2, K因子, 升力曲线斜率
- **推力参数**：各阶段最大推力、高度系数、马赫系数
- **燃油参数**：CF1, CF2, CR1, CR2, CD1, CD2
- **质量参数**：最大起飞质量、最大着陆质量、操作空重
- **几何参数**：机翼面积、翼展、展弦比

## 算法说明

### 龙格-库塔法 (RK4)
```
k1 = h * f(t, y)
k2 = h * f(t + h/2, y + k1/2)
k3 = h * f(t + h/2, y + k2/2)
k4 = h * f(t + h, y + k3)
y(t + h) = y(t) + (k1 + 2*k2 + 2*k3 + k4) / 6
```
其中状态向量 y = [纬度, 经度, 高度, 速度, 航向, 俯仰角, 质量, 燃油]

### 飞行动力学方程
- 水平运动：考虑地速和风速的矢量叠加
- 垂直运动：dh/dt = (T-D)V/W
- 质量变化：dm/dt = -fuelFlow
- 导航逻辑：沿大圆航线飞向航路点

### 风温场插值
三维反距离加权插值（IDW）：
```
w_i = 1 / d_i^p
value = Σ(w_i * value_i) / Σ(w_i)
```
其中 p=2（权重衰减指数）

## API 接口

### REST API
| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/trajectory/flights` | 获取所有航班 |
| GET | `/api/trajectory/{flightId}` | 获取航班详情 |
| POST | `/api/trajectory/calculate/{flightId}` | 计算航迹 |
| POST | `/api/trajectory/stream/start/{flightId}` | 开始流式推送 |
| POST | `/api/trajectory/stream/stop/{flightId}` | 停止流式推送 |
| GET | `/api/bada/aircraft` | 获取所有机型 |
| GET | `/api/bada/performance/{type}` | 获取机型性能 |
| POST | `/api/bada/upload` | 上传BADA文件 |

### WebSocket 主题
| 主题 | 描述 |
|------|------|
| `/topic/trajectory/{flightId}` | 航迹点实时推送 |
| `/topic/status/{flightId}` | 飞行状态更新 |
| `/topic/notifications` | 系统通知 |
| `/topic/weather` | 气象数据更新 |

## 快速开始

### 环境要求
- JDK 17+
- Maven 3.8+
- PostgreSQL 14+
- Node.js 18+
- npm 9+

### 1. 数据库初始化
```bash
psql -U postgres < database/init.sql
```

### 2. 后端启动
```bash
cd backend
mvn clean compile
mvn spring-boot:run
```
后端将在 `http://localhost:8080` 启动

### 3. 前端启动
```bash
cd frontend
npm install
npm run dev
```
前端将在 `http://localhost:3000` 启动

### 4. 运行流程
1. 启动 PostgreSQL 数据库并执行初始化脚本
2. 启动 Spring Boot 后端
3. 启动 React 前端
4. 在前端页面选择航班
5. 点击 "Calculate Trajectory" 计算航迹
6. 点击 "Start Stream" 开始实时推送
7. 在 3D 地球上查看动态航迹

## 示例飞行计划

### MU5101 (北京首都 → 上海浦东)
- 机型：A320
- 航距：约1080公里
- 巡航高度：10668米 (35000英尺)
- 巡航马赫数：0.78
- 航路点：ZBAA → VYK → YQG → WXD → PIMEX → ZSPD

### CA1352 (广州白云 → 北京大兴)
- 机型：B737-800
- 航距：约1880公里
- 巡航高度：11582米 (38000英尺)
- 巡航马赫数：0.785
- 航路点：ZGGG → GULEK → LKN → BTO → ZBAD

## 性能特点

1. **高精度数值积分**：RK4算法截断误差为O(h⁴)
2. **真实空气动力学**：基于BADA数据的完整推力/阻力模型
3. **气象影响**：考虑高空风对飞行时间和燃油消耗的影响
4. **实时可视化**：WebSocket推送延迟<100ms
5. **可扩展性**：模块化设计，易于添加新机型和新算法

## 开发说明

### BADA数据格式
BADA数据使用CSV格式，每行一个参数：
```csv
parameter_name,value,unit,description
CD0,0.0200,dimensionless,Zero-lift drag coefficient
CD2,0.0400,dimensionless,Induced drag factor
...
```

### 配置文件
后端配置在 `application.yml`：
- 数据库连接参数
- BADA文件路径
- 航迹计算参数（时间步长、最大迭代次数）
- WebSocket配置

## 参考资料

- [EUROCONTROL BADA User Manual](https://www.eurocontrol.int/model/bada)
- [Cesium.js Documentation](https://cesium.com/docs/)
- [Spring WebSocket](https://docs.spring.io/spring-framework/reference/web/websocket.html)
- [Runge-Kutta Methods](https://en.wikipedia.org/wiki/Runge%E2%80%93Kutta_methods)

## License

MIT License
