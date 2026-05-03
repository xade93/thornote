#pragma once

#include "cls_process.h"
#include "det_process.h"
#include "paddle_api.h"
#include "rec_process.h"
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <string>
#include <vector>

using namespace paddle::lite_api; // NOLINT

class Pipeline {
public:
  Pipeline(const std::string &detModelDir, const std::string &clsModelDir,
           const std::string &recModelDir, const std::string &cpuPowerMode,
           int cpuThreadNum, const std::string &configPath,
           const std::string &dictPath);

  std::string Recognize(const cv::Mat &bgrImage);

private:
  std::map<std::string, double> config_;
  std::vector<std::string> characterDict_;
  std::shared_ptr<ClsPredictor> clsPredictor_;
  std::shared_ptr<DetPredictor> detPredictor_;
  std::shared_ptr<RecPredictor> recPredictor_;
};

