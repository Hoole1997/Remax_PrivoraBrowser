一 服务概述
所有天气数据均来源于 AccuWeather 平台: https://developer.accuweather.com/home
本服务将所有请求的天气数据缓存24小时，减少对平台API的调用次数。

二 接口设计逻辑
本服务只提供一个接口，通过 op 参数区分不同的操作。目前有三种操作:
1：通过使用者IP地址查询天气。无需其他参数，将直接返回IP所在地天气。
2：通过 location key 查看天气。需提供参数 key，将直接返回指定城市或地区天气。
3：通过地名查看天气。需提供参数 addr，指定地名。注意，地名需使用 URL.QueryEscape() 处理。另外因为数据提供平台是美国的，不支持中文地名。查找结果有三种情况:
1. 找不到。返回错误，然后让用户重新输入。
2. 找到不止一个地点。此时会返回多个地点的信息，需要让用户选择一个。然后可以从返回的结果中找到对应地点的 location key，然后可以通过这个 location key 使用 op=2 再次调用接口查询天气。
3. 找到唯一一个地点，此时会直接返回当地的天气情况。

三 接口调用
域名: http://api.blazelabstudio.com。接口名随意。
调用方法: GET
参数:
op: 请求类型
1：通过ip地址查看天气，无需其他参数
2：通过 location key 查看天气，需要提供 key
3：通过地名查看天气，需要提供 Addr
key：地点的唯一标识码
addr：地名
调用 url 示例: http://api.blazelabstudio.com/query?op=3&addr=ChengDu         http://api.blazelabstudio.com/query?op=1
这里使用的接口名是 query，不过实际上可以是任意名字。

返回结构（json格式）：
code(int)：返回码
0：成功。如果是通过地名查询，表示找到唯一地点。
1：查询天气失败。
2：查询地名失败。
3：根据地名查询到多个地址，需要确认具体地址。
locationKey(string): 地点唯一标识码
locations(json string)：通过地名搜索天气时，有可能搜索到多个地点，这里会返回搜索结果。待用户确认后，可以调用查询接口，通过 locationKey 再次查询。
currentConditions(json string)：当前天气情况
dailyForecasts(json string)：未来天气预报，默认应该是5天的
city(json string): 城市信息。通过ip查看时会返回这个

四 返回数据结构
返回数据中 code 和 locationKey 就是字面值。另外三个均为 json 编码的 字符串。
locations：https://developer.accuweather.com/core-weather/text-search#city-search
currentConditions:  https://developer.accuweather.com/core-weather/location-key-currentconditions#current-conditions-by-location-key
dailyForecasts: https://developer.accuweather.com/core-weather/location-key-daily#5-days-by-location-key
以下代码是 golang 的，供参考:
Locations 返回的值是：[]CityInfo。CityInfo 中的 key 就是我们需要的 locationKey。
CurrentConditions 返回值是: CurrentConditionResponse
DailyForecasts 返回值是: DayForecastsResponse
City 返回值是: CityInfo

五 调用签名
为了防止接口泄漏，使用了签名校验。签名校验相关内容设置在请求的 Header 中（以下为golang代码，供参考）:
request.Header.Add("Authorization", appKey)

          timestamp := fmt.Sprintf("%d", time.Now().Unix())
    request.Header.Add("Timestamp", timestamp)
    
    temp := fmt.Sprintf("%s-%s-%s", appKey, timestamp, appSecret)
    h := md5.New()
    h.Write([]byte(temp))
    Signature := fmt.Sprintf("%x", h.Sum(nil))
    request.Header.Add("Sign", signature)
    
    其中: 
    appKey:  weatherQuery1
    appSecret: secret1
    Timestamp 的值为当前时间戳，取 1970年1月1日到现在的时间，单位为秒。
    待加密字符串 temp 看起来这样: weatherQuery1-1762226355-secret1


数据结构定义参考：

// 国家或地区信息
type RegionOrCountry struct {
ID            string `json:"ID,omitempty"`
LocalizedName string `json:"LocalizedName,omitempty"`
EnglishName   string `json:"EnglishName,omitempty"`
}

// 时区
type TimeZone struct {
Code             string  `json:"Code,omitempty"`
NextOffsetChange string  `json:"NextOffsetChange,omitempty"`
Name             string  `json:"Name,omitempty"`
GmtOffset        float32 `json:"GmtOffset,omitempty"`
IsDaylightSaving bool    `json:"IsDaylightSaving,omitempty"`
}

// 地理信息
type GeoPosition struct {
Elevation Units   `json:"Elevation,omitempty"` // 海拔
Latitude  float32 `json:"Latitude,omitempty"`  // 纬度
Longitude float32 `json:"Longitude,omitempty"` // 经度
}

// 行政区
type AdministrativeArea struct {
Level         int64  `json:"Level,omitempty"`         // 等级
CountryID     string `json:"CountryId,omitempty"`     // 国家id
ID            string `json:"ÏD,omitempty"`            // 行政区id
LocalizedName string `json:"LocalizedName,omitempty"` // 本地化名字
EnglishName   string `json:"EnglishName,omitempty"`   // 英文名
LocalizedType string `json:"LocalizedType,omitempty"` // 本地化行政区类型
EnglishType   string `json:"EnglishType,omitempty"`   // 英文行政区类型
}

// 单位
type UnitInfo struct {
Value    float32 `json:"Value,omitempty"`    // 具体数值
Unit     string  `json:"Unit,omitempty"`     // 单位，表示温度是 "C" 或者 "F"，表示高度是 "m" 或者 "ft"
UnitType int64   `json:"UnitType,omitempty"` // 单位类型，"C" 对应 17，"F" 对应 18，"m" 对应 5，"ft" 对应 0
Phrase   string  `json:"Phrase,omitempty"`   // 体感
}

// 单位数值。如温度，海拔，速度等。对应公制单位数值和英制单位数值。
type Units struct {
Metric   UnitInfo `json:"Metric,omitempty"`   // 公制单位数值，如摄氏度(C)，m，km/h
Imperial UnitInfo `json:"Imperial,omitempty"` // 英制单位数值，如华氏度(F)，ft，mi/h
}

// 单位值范围
type UnitsRange struct {
Minimum Units `json:"Minimum,omitempty"`
Maximum Units `json:"Maximum,omitempty"`
Average Units `json:"Average,omitempty"` // 大部分返回值不包含这个值
}

// 数值范围
type NumbersRange struct {
Minimum float32 `json:"Minimum,omitempty"`
Maximum float32 `json:"Maximum,omitempty"`
Average float32 `json:"Average,omitempty"` // 大部分返回值不包含这个值
}

// 风向
type Direction struct {
Degrees   int32  `json:"Degrees,omitempty"`   // Wind direction in azimuth degrees from north (180° indicates a wind coming from the south).
Localized string `json:"Localized,omitempty"` // A wind direction abbreviation in the specified language.
English   string `json:"English,omitempty"`   // An English wind direction abbreviation.
}

// 风
type Wind struct {
Direction Direction `json:"Direction,omitempty"` // An object containing wind direction in azimuth degrees.
Speed     Units     `json:"Speed,omitempty"`     // 风速
}

// 气压变化趋势
type PressureTendency struct {
LocalizedText string `json:"localizedText,omitempty"`
Code          string `json:"code,omitempty"`
}

// 降水量汇总
type PrecipitationSummary struct {
PastHour    Units `json:"PastHour,omitempty"`
Past3Hours  Units `json:"Past3Hours,omitempty"`
Past6Hours  Units `json:"Past6Hours,omitempty"`
Past9Hours  Units `json:"Past9Hours,omitempty"`
Past12Hours Units `json:"Past12Hours,omitempty"`
Past18Hours Units `json:"Past18Hours,omitempty"`
Past24Hours Units `json:"Past24Hours,omitempty"`
}

// 温度信息汇总
type TemperatureSummary struct {
Past6HourRange  UnitsRange `json:"Past6HourRange,omitempty"`
Past12HourRange UnitsRange `json:"Past12HourRange,omitempty"`
Past24HourRange UnitsRange `json:"Past24HourRange,omitempty"`
}

// 太阳和月亮信息
type SunAndMoon struct {
Rise      string `json:"Rise,omitempty"`      // Rise displayed in ISO8601 format: (yyyy-mm-ddThh:mm:ss±hh:mm).
EpochRise int64  `json:"EpochRise,omitempty"` // Rise displayed as the number of seconds that have elapsed since January 1, 1970 (midnight UTC/GMT).
Set       string `json:"Set,omitempty"`       // Set displayed in ISO8601 format: (yyyy-mm-ddThh:mm:ss±hh:mm).
EpochSet  int64  `json:"EpochSet,omitempty"`  // Set displayed as the number of seconds that have elapsed since January 1, 1970 (midnight UTC/GMT).
Phase     string `json:"Phase,omitempty"`     // 月相(只有月亮有)
Age       int32  `json:"Age,omitempty"`       // The number of days since the new moon. (只有月亮有)
}

// Summary of Heating Degree Day or Cooling Degree Day information.
type DegreeDaySummary struct {
Heating UnitInfo `json:"Heating,omitempty"` // Number of degrees that the mean temperature is below 65 degrees F. An object with a rounded value in the specified units, Fahrenheit or Celsius. The object may be NULL.
Cooling UnitInfo `json:"Cooling,omitempty"` // Number of degrees that the mean temperature is above 65 degrees F. An object with a rounded value in the specified units, Fahrenheit or Celsius. The object may be NULL.
}

// 空气和花粉
type AirAndPollen struct {
Name          string `json:"Name,omitempty"`          // Name of the pollen or pollutant. For example: grass, mold, weed, air quality, tree and UV index.
Value         int32  `json:"Value,omitempty"`         // Value of the given type above. Values associated with mold, grass, weed and tree are in units of parts per cubic meter. Both air quality and UV are indices, so they are unitless.
Category      string `json:"Category,omitempty"`      // Category of the air quality or pollution type. For example: low, high, good, moderate, unhealthy, hazardous.
CategoryValue int32  `json:"CategoryValue,omitempty"` // Value associated with the air quality or pollution category. These values range from 1 to 6. 1 implying good conditions, 6 implying hazardous conditions.
Type          string `json:"Type,omitempty"`          // Only exists for air quality. Examples include ozone and particle pollution.
}

// 预报总览
type Headline struct {
EffectiveDate      string `json:"EffectiveDate,omitempty"`      // Datetime displayed in ISO8601 format: yyyy-mm-ddThh:mm:ss±hh:mm that the headline is in effect.
EffectiveEpochDate int64  `json:"EffectiveEpochDate,omitempty"` // Effective datetime of the headline displayed as the number of seconds that have elapsed since January 1, 1970 (midnight UTC/GMT).
Severity           int32  `json:"Severity,omitempty"`           // Severity of the headline displayed as an integer. The lower the number, the greater the severity.
Text               string `json:"Text,omitempty"`               // Text of the headline.
Category           string `json:"Category,omitempty"`           // Category of the headline.
EndDate            string `json:"EndDate,omitempty"`            // Datetime displayed in ISO8601 format: yyyy-mm-ddThh:mm:ss±hh:mm that the headline period ends.
EndEpochDate       int64  `json:"EndEpochDate,omitempty"`       // End datetime of the headline displayed as the number of seconds that have elapsed since January 1, 1970 (midnight UTC/GMT).
MobileLink         string `json:"MobileLink,omitempty"`         // Link to current conditions on AccuWeather's mobile site for the requested location.
Link               string `json:"Link,omitempty"`               // Link to current conditions on AccuWeather's free site for the requested location.
}

// 天气情况
type WeatherInfo struct {
Icon                    int32        `json:"Icon,omitempty"`                    // 天气图标
IconPhrase              string       `json:"IconPhrase,omitempty"`              // Phrase description of the Icon.
HasPrecipitation        bool         `json:"HasPrecipitation,omitempty"`        // 是否有降水
PrecipitationType       string       `json:"PrecipitationType,omitempty"`       // 降水是雨，雪，或其他什么东西。当 HasPrecipitation 为 true 时会有
PrecipitationIntensity  string       `json:"PrecipitationIntensity,omitempty"`  // 降水强度
ShortPhrase             string       `json:"ShortPhrase,omitempty"`             // Phrase description of the forecast. (Note: AccuWeather attempts to keep this phrase under 30 characters in length, but some languages/weather events may result in a longer phrase length, exceeding 30 characters.).
LongPhrase              string       `json:"LongPhrase,omitempty"`              // Phrase description of the forecast. (Note: AccuWeather attempts to keep this phrase under 100 characters in length, but some languages/weather events may result in a longer phrase length, exceeding 100 characters.).
PrecipitionProbability  int32        `json:"PrecipitionProbability,omitempty"`  // 降水概率
ThunderstormProbability int32        `json:"ThunderstormProbability,omitempty"` // A percentage value representing the probability of a thunderstorm.
RainProbability         int32        `json:"RainProbability,omitempty"`         // A percentage value representing the probability of rain.
SnowProbability         int32        `json:"SnowProbability,omitempty"`         // A percentage value representing the probability of snow.
IceProbability          int32        `json:"IceProbability,omitempty"`          // A percentage value representing the probability of ice.
Wind                    Wind         `json:"Wind,omitempty"`                    // 风
WindGust                Wind         `json:"WindGust,omitempty"`                // 最大短时阵风
TotalLiquid             UnitInfo     `json:"TotalLiquid,omitempty"`             // 12小时总降水量
Rain                    UnitInfo     `json:"Rain,omitempty"`                    // 12小时总降雨量
Snow                    UnitInfo     `json:"Snow,omitempty"`                    // 12小时总降雪量
Ice                     UnitInfo     `json:"Ice,omitempty"`                     // 12小时降冰雹总量
HoursOfPrecipitation    float32      `json:"HoursOfPrecipitation,omitempty"`    // 在12小时中的降水总小时数
HoursOfRain             float32      `json:"HoursOfRain,omitempty"`             // 在12小时中的降雨总小时数
HoursOfSnow             float32      `json:"HoursOfSnow,omitempty"`             // 在12小时中的降雪总小时数
HoursOfIce              float32      `json:"HoursOfIce,omitempty"`              // 在12小时中的降冰雹总小时数
CloudCover              int32        `json:"CloudCover,omitempty"`              // The percentage of cloud cover.
Evapotranspiration      UnitInfo     `json:"Evapotranspiration,omitempty"`      // 蒸发量
SolarIrradiance         UnitInfo     `json:"SolarIrradiance,omitempty"`         // 太阳辐照度
RelativeHumidity        NumbersRange `json:"RelativeHumidity,omitempty"`        // 空气湿度范围
WetBulbTemperature      UnitsRange   `json:"WetBulbTemperature,omitempty"`      // The temperature to which air may be cooled by evaporating water into it at constant pressure until it reaches saturation. The object contains a rounded value in the specified units, Fahrenheit or Celsius. The object may be NULL.
WebBulbGlobeTemperature UnitsRange   `json:"WebBulbGlobeTemperature,omitempty"` // A temperature value that indicates heat stress on the human body in direct sunlight based on temperature, humidity, wind speed, sun angle, and cloud cover. The object contains a rounded value in the specified units, Fahrenheit or Celsius. The object may be NULL.
UVIndexFloat            NumbersRange `json:"UVIndexFloat,omitempty"`            // An object containing the sun's measured ultraviolet radiation strength.
}

// 每日预报
type DailyForecast struct {
Date                     string           `json:"Date,omitempty"`                     // Date of the forecast displayed in ISO8601 format: (yyyy-mm-ddThh:mm:ss±hh:mm).
EpochDate                int64            `json:"EpochDate,omitempty"`                // Date of the forecast displayed as the number of seconds that have elapsed since January 1, 1970 (midnight UTC/GMT).
Temperature              UnitsRange       `json:"Temperature,omitempty"`              // 预测温度
Day                      WeatherInfo      `json:"Day,omitempty"`                      // 白天天气
Night                    WeatherInfo      `json:"Night,omitempty"`                    // 夜晚天气
Sources                  []string         `json:"Sources,omitempty"`                  // List of forecast sources.
MobileLink               string           `json:"MobileLink,omitempty"`               // Link to daily forecasts on AccuWeather's mobile site for the requested location.
Link                     string           `json:"Link,omitempty"`                     // Link to daily forecasts on AccuWeather's free site for the requested location.
Sun                      SunAndMoon       `json:"SunAndMoon,omitempty"`               // 太阳
Moon                     SunAndMoon       `json:"Moon,omitempty"`                     // 月亮
RealFeelTemperature      UnitsRange       `json:"RealFeelTemperature,omitempty"`      // 体感温度
RealFeelTemperatureShade UnitsRange       `json:"RealFeelTemperatureShade,omitempty"` // An object containing the patented AccuWeather RealFeel™ shade temperature with a rounded value in the specified units, Fahrenheit or Celsius. The object may be NULL.
HoursOfSun               float32          `json:"HoursOfSun,omitempty"`               // 太阳出现的时间(小时数)
DegreeDaySummary         DegreeDaySummary `json:"DegreeDaySummary,omitempty"`         // Summary of Heating Degree Day or Cooling Degree Day information.
AirAndPollen             []AirAndPollen   `json:"AirAndPollen,omitempty"`             // 空气和花粉
}

// 城市信息
type CityInfo struct {
PrimaryPostalCode      string               `json:"PrimaryPostalCode,omitempty"`      // 邮编
Region                 RegionOrCountry      `json:"Region,omitempty"`                 // 地区
Country                RegionOrCountry      `json:"Country,omitempty"`                // 国家
TimeZone               TimeZone             `json:"TimeZone,omitempty"`               // 时区
GeoPosition            GeoPosition          `json:"GeoPosition,omitempty"`            // 地理信息
IsAlias                bool                 `json:"IsAlias,omitempty"`                // 这个城市是否是别名
SupplementalAdminAreas []AdministrativeArea `json:"SupplementalAdminAreas,omitempty"` // 包含行政区
DataSets               []string             `json:"DataSets,omitempty"`               // 当前区域包含的气象信息，如: "AirQuality", "MinuteCast"...
EnglishName            string               `json:"EnglishName,omitempty"`            // 英文名
Version                int32                `json:"Version,omitempty"`                // 当前API版本
Key                    string               `json:"Key,omitempty"`                    // 地区的唯一id，用这个来查询天气
Type                   string               `json:"Type,omitempty"`                   // 地点类型，包括: City, PostalCode, POI, LatLong
Rank                   int32                `json:"Rank,omitempty"`                   // Number applied to locations set by factors such as population, political importance, and geographic size
LocalizedName          string               `json:"LocalizedName,omitempty"`          // 本地化名称
AdministrativeArea     AdministrativeArea   `json:"AdministrativeArea,omitempty"`     // 所属行政区
}

// 预报信息
type DayForecastsResponse struct {
Headline       Headline        `json:"Headline,omitempty"`
DailyForecasts []DailyForecast `json:"DailyForecasts,omitempty"`
}

// 获取当前天气情况响应
type CurrentConditionResponse struct {
LocalObservationDateTime       string               `json:"LocalObservationDateTime,omitempty"`       // 本地观测时间
EpochTime                      int64                `json:"EpochTime,omitempty"`                      // 数据采集时间，精确到秒，从 1970 年开始的 utc 时间
WeatherText                    string               `json:"WeatherText,omitempty"`                    // 天气文本描述
WeatherIcon                    int32                `json:"WeatherIcon,omitempty"`                    // 天气图标id，具体图标见: https://developer.accuweather.com/documentation/weather-icons
HasPrecipitation               bool                 `json:"HasPrecipitation,omitempty"`               // 是否有降水
PrecipitationType              string               `json:"PrecipitationType,omitempty"`              // 降雨类型
IsDayTime                      bool                 `json:"IsDayTime,omitempty"`                      // 是否是白天
Temperature                    Units                `json:"Temperature,omitempty"`                    // 温度信息
MobileLink                     string               `json:"MobileLink,omitempty"`                     // Link to current conditions on AccuWeather's mobile site for the requested location.
Link                           string               `json:"Link,omitempty"`                           // Link to current conditions on AccuWeather's free site for the requested location.
RealFeelTemperatureShade       Units                `json:"RealFeelTemperatureShade,omitempty"`       // 体感温度
RelativeHumidity               int32                `json:"RelativeHumidity,omitempty"`               // 相对湿度
IndoorRelativeHumidity         int32                `json:"IndoorRelativeHumidity,omitempty"`         // 室内相对湿度
DewPoint                       Units                `json:"DewPoint,omitempty"`                       // 露点温度
Wind                           Wind                 `json:"Wind,omitempty"`                           // 风向和风速
WindGust                       Wind                 `json:"WindGust,omitempty"`                       // 短时阵风强度
UVIndex                        int32                `json:"UVIndex,omitempty"`                        // 紫外线强度
UVIndexFloat                   float32              `json:"UVIndexFloat,omitempty"`                   // 紫外线强度
UVIndexText                    string               `json:"UVIndexText,omitempty"`                    // 紫外线强度描述
Visibility                     Units                `json:"Visibility,omitempty"`                     // 能见度(距离)
ObstructionsToVisibility       string               `json:"ObstructionsToVisibility,omitempty"`       // The visibility distance's limiting factor.
CloudCover                     int32                `json:"CloudCover,omitempty"`                     // A number representing the percentage of the sky that is covered by clouds. May be NULL.
Ceiling                        Units                `json:"Ceiling,omitempty"`                        // 云层大致高度. May be NULL.
Pressure                       Units                `json:"Pressure,omitempty"`                       // 气压
PressureTendency               PressureTendency     `json:"PressureTendency,omitempty"`               // 气压变化趋势
Past24HourTemperatureDeparture Units                `json:"Past24HourTemperatureDeparture,omitempty"` // 过去24小时温度偏差
WindChillTemperature           Units                `json:"WindChillTemperature,omitempty"`           // An object containing information about perceived air temperature on exposed skin due to wind with rounded values for Fahrenheit and Celsius.
WetBulbTemperature             Units                `json:"WetBulbTemperature,omitempty"`             // The temperature to which air may be cooled by evaporating water into it at constant pressure until it reaches saturation. An object containing a rounded value for Fahrenheit and Celsius.
WebBulbGlobeTemperature        Units                `json:"WebBulbGlobeTemperature,omitempty"`        // A temperature value that indicates heat stress on the human body in direct sunlight based on temperature, humidity, wind speed, sun angle, and cloud cover. An object containing a rounded value for Fahrenheit and Celsius.
Precip1hr                      Units                `json:"Precip1hr,omitempty"`                      // Amount of liquid water equivalent of precipitation that has fallen in the past hour. An object containing a rounded value for Inch and Millimeter.
PrecipitationSummary           PrecipitationSummary `json:"PrecipitationSummary,omitempty"`           // 降水量汇总
TemperatureSummary             TemperatureSummary   `json:"TemperatureSummary,omitempty"`             // 温度汇总
ApparentTemperature            Units                `json:"ApparentTemperature,omitempty"`            // Perceived outdoor temperature caused by the combination of air temperature, relative humidity, and wind speed.
}