# One-time imports of what was built in the console (docs/AWS_DEPLOYMENT.md §4).

import {
  to = aws_security_group.web
  id = "sg-0494364a03526622b"
}
import {
  to = aws_security_group.db
  id = "sg-0a8511e0c3c9f0f86"
}
import {
  to = aws_instance.server
  id = "i-02d65fab4965cb3c4"
}
import {
  to = aws_eip.server
  id = "eipalloc-082c75d049e9df296"
}
import {
  to = aws_eip_association.server
  id = "eipassoc-0122f7141aadfb9b8"
}
import {
  to = aws_db_instance.main
  id = "battle-royal-db"
}
import {
  to = aws_cloudwatch_metric_alarm.ec2_system_check
  id = "br-ec2-system-check"
}
import {
  to = aws_cloudwatch_metric_alarm.ec2_instance_check
  id = "br-ec2-instance-check"
}
import {
  to = aws_cloudwatch_metric_alarm.ec2_cpu_credits
  id = "br-ec2-cpu-credits"
}
import {
  to = aws_cloudwatch_metric_alarm.rds_storage
  id = "br-rds-storage"
}
